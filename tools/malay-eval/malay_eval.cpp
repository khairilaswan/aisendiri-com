// Runs a set of chat prompts through a GGUF model the same way the app does
// (llama/src/main/cpp/aisendiri_llama.cpp): the model's built-in chat template via
// llama_chat_apply_template with a ChatML fallback, and the same sampler chain.
//
// Usage: malay-eval <model.gguf> <prompts.json> <out.jsonl> [--no-think]
//
// --no-think adds Qwen3's "/no_think" switch to the system prompt and drops the
// empty <think></think> block from replies.

#include "llama.h"
#include "nlohmann/json.hpp"

#include <chrono>
#include <cstdio>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

using json = nlohmann::json;

namespace {

// Same defaults as AppSettings and Recommender.defaultConfig for a 4 GB phone.
constexpr int N_CTX = 2048;
constexpr int N_BATCH = 256;
constexpr float TEMPERATURE = 0.7f;
constexpr float TOP_P = 0.9f;
constexpr float MIN_P = 0.05f;
constexpr float REPEAT_PENALTY = 1.1f;
constexpr int MAX_REPLY_TOKENS = 512;
constexpr uint32_t SEED = 42;

std::string token_to_piece(const llama_vocab *vocab, llama_token token) {
    char buf[256];
    int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, false);
    if (n < 0) {
        std::string big(static_cast<size_t>(-n), '\0');
        n = llama_token_to_piece(vocab, token, big.data(), -n, 0, false);
        return n < 0 ? std::string() : big.substr(0, static_cast<size_t>(n));
    }
    return std::string(buf, static_cast<size_t>(n));
}

std::string format_chat(const llama_model *model, const std::vector<std::pair<std::string, std::string>> &msgs) {
    std::vector<llama_chat_message> chat;
    size_t total = 0;
    for (auto &m : msgs) {
        chat.push_back({m.first.c_str(), m.second.c_str()});
        total += m.second.size();
    }
    const char *tmpl = llama_model_chat_template(model, nullptr);
    std::vector<char> buf(total * 2 + 1024);
    int len = llama_chat_apply_template(tmpl, chat.data(), chat.size(), true, buf.data(), (int32_t) buf.size());
    if (len < 0 && tmpl != nullptr) {
        tmpl = "chatml";
        len = llama_chat_apply_template(tmpl, chat.data(), chat.size(), true, buf.data(), (int32_t) buf.size());
    }
    if (len < 0) return {};
    if (static_cast<size_t>(len) > buf.size()) {
        buf.resize(static_cast<size_t>(len));
        len = llama_chat_apply_template(tmpl, chat.data(), chat.size(), true, buf.data(), (int32_t) buf.size());
    }
    return std::string(buf.data(), static_cast<size_t>(len));
}

std::string strip_think(std::string s) {
    const auto open = s.find("<think>");
    const auto close = s.find("</think>");
    if (open != std::string::npos && close != std::string::npos && close > open) {
        s.erase(open, close + 8 - open);
    }
    const auto start = s.find_first_not_of(" \n");
    return start == std::string::npos ? std::string() : s.substr(start);
}

} // namespace

int main(int argc, char **argv) {
    if (argc < 4) {
        std::cerr << "usage: malay-eval <model.gguf> <prompts.json> <out.jsonl> [--no-think]\n";
        return 2;
    }
    const bool no_think = argc > 4 && std::string(argv[4]) == "--no-think";

    std::ifstream pf(argv[2]);
    const json spec = json::parse(pf);

    llama_backend_init();
    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    llama_model *model = llama_model_load_from_file(argv[1], model_params);
    if (!model) {
        std::cerr << "failed to load model\n";
        return 1;
    }
    const llama_vocab *vocab = llama_model_get_vocab(model);
    char desc[256];
    llama_model_desc(model, desc, sizeof(desc));

    std::ofstream out(argv[3]);
    for (const auto &p : spec["prompts"]) {
        auto ctx_params = llama_context_default_params();
        ctx_params.n_ctx = N_CTX;
        ctx_params.n_batch = N_BATCH;
        ctx_params.n_ubatch = N_BATCH;
        llama_context *ctx = llama_init_from_model(model, ctx_params);

        std::string system = spec["system"].get<std::string>();
        if (p.value("voice", false)) system += "\n\n" + spec["voice_style"].get<std::string>();
        if (no_think) system += " /no_think";
        std::vector<std::pair<std::string, std::string>> msgs = {{"system", system}};
        for (const auto &m : p["messages"]) msgs.emplace_back(m["role"], m["content"]);

        const std::string prompt = format_chat(model, msgs);
        std::vector<llama_token> tokens(prompt.size() + 16);
        const int n = llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), tokens.data(),
                                     (int32_t) tokens.size(), true, true);
        tokens.resize(n > 0 ? n : 0);

        auto *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(smpl, llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, REPEAT_PENALTY, 0.0f, 0.0f));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(TOP_P, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_min_p(MIN_P, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(TEMPERATURE));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(SEED));

        const auto t0 = std::chrono::steady_clock::now();
        for (size_t i = 0; i < tokens.size(); i += N_BATCH) {
            const int nb = (int) std::min<size_t>(N_BATCH, tokens.size() - i);
            llama_decode(ctx, llama_batch_get_one(tokens.data() + i, nb));
        }
        const auto t1 = std::chrono::steady_clock::now();

        std::string reply;
        int generated = 0;
        bool cut = true;
        while (generated < MAX_REPLY_TOKENS) {
            llama_token tok = llama_sampler_sample(smpl, ctx, -1);
            if (llama_vocab_is_eog(vocab, tok)) {
                cut = false;
                break;
            }
            reply += token_to_piece(vocab, tok);
            generated++;
            if (llama_decode(ctx, llama_batch_get_one(&tok, 1)) != 0) break;
        }
        const auto t2 = std::chrono::steady_clock::now();
        const double prompt_s = std::chrono::duration<double>(t1 - t0).count();
        const double gen_s = std::chrono::duration<double>(t2 - t1).count();

        json row = {
            {"id", p["id"]},
            {"model", desc},
            {"reply", no_think ? strip_think(reply) : reply},
            {"prompt_tokens", tokens.size()},
            {"reply_tokens", generated},
            {"cut_short", cut},
            {"prompt_tps", prompt_s > 0 ? tokens.size() / prompt_s : 0},
            {"gen_tps", gen_s > 0 ? generated / gen_s : 0},
        };
        out << row.dump() << "\n";
        out.flush();
        std::cout << "== " << p["id"].get<std::string>() << " (" << generated << " tokens, "
                  << row["gen_tps"].get<double>() << " tok/s)\n" << row["reply"].get<std::string>() << "\n\n";

        llama_sampler_free(smpl);
        llama_free(ctx);
    }
    llama_model_free(model);
    llama_backend_free();
    return 0;
}
