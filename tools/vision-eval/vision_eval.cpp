// Asks a vision AI questions about test images the same way the app does
// (llama/src/main/cpp/aisendiri_llama.cpp): the model's built-in chat template with the
// media marker in the user message, libmtmd for the image, and the app's sampler chain.
//
// Usage: vision-eval <model.gguf> <mmproj.gguf> <tasks.json> <out.jsonl>
//                    [--describe <text-model.gguf>] [--max-image-tokens N] [--no-think]
//
// Default mode: the vision AI answers each question directly.
// --describe: the vision AI writes an English description of the image, then the text AI
// (the app's Light chat AI) answers the question in Malay from that description.

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"
#include "nlohmann/json.hpp"

#include <chrono>
#include <cstdio>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

using json = nlohmann::json;
using clk = std::chrono::steady_clock;

namespace {

// Same defaults as the app on a 4 GB phone, with a larger context for the image tokens.
constexpr int N_CTX = 4096;
constexpr int N_BATCH = 256;
constexpr int N_THREADS = 4;
constexpr float TEMPERATURE = 0.7f;
constexpr float TOP_P = 0.9f;
constexpr float MIN_P = 0.05f;
constexpr float REPEAT_PENALTY = 1.1f;
constexpr int MAX_REPLY_TOKENS = 400;
constexpr uint32_t SEED = 42;

double secs(clk::time_point a, clk::time_point b) { return std::chrono::duration<double>(b - a).count(); }

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

// Gemma 4 has no built-in template in llama.cpp, so it is written out here (same as the app).
std::string format_gemma4(const std::vector<std::pair<std::string, std::string>> &msgs, bool empty_thought) {
    std::string out;
    for (auto &m : msgs) {
        const std::string role = m.first == "assistant" ? "model" : m.first;
        out += "<|turn>" + role + "\n" + m.second + "<turn|>\n";
    }
    out += "<|turn>model\n";
    if (empty_thought) out += "<|channel>thought\n<channel|>";
    return out;
}

std::string format_chat(const llama_model *model, const std::vector<std::pair<std::string, std::string>> &msgs) {
    const char *tmpl = llama_model_chat_template(model, nullptr);
    if (tmpl && std::string(tmpl).find("<|turn>") != std::string::npos) {
        return format_gemma4(msgs, std::string(tmpl).find("<|channel>thought") != std::string::npos);
    }
    std::vector<llama_chat_message> chat;
    size_t total = 0;
    for (auto &m : msgs) {
        chat.push_back({m.first.c_str(), m.second.c_str()});
        total += m.second.size();
    }
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
    if (open != std::string::npos && close != std::string::npos && close > open) s.erase(open, close + 8 - open);
    const auto start = s.find_first_not_of(" \n");
    return start == std::string::npos ? std::string() : s.substr(start);
}

llama_context *new_context(llama_model *model) {
    auto p = llama_context_default_params();
    p.n_ctx = N_CTX;
    p.n_batch = N_BATCH;
    p.n_ubatch = N_BATCH;
    p.n_threads = N_THREADS;
    p.n_threads_batch = N_THREADS;
    return llama_init_from_model(model, p);
}

llama_sampler *new_sampler(const llama_vocab *vocab) {
    auto *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, REPEAT_PENALTY, 0.0f, 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(TOP_P, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(MIN_P, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(TEMPERATURE));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(SEED));
    return smpl;
}

struct Reply {
    std::string text;
    int generated = 0;
    bool cut = true;
    double gen_s = 0;
};

Reply generate(llama_context *ctx, const llama_vocab *vocab) {
    Reply r;
    auto *smpl = new_sampler(vocab);
    const auto t0 = clk::now();
    while (r.generated < MAX_REPLY_TOKENS) {
        llama_token tok = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) {
            r.cut = false;
            break;
        }
        r.text += token_to_piece(vocab, tok);
        r.generated++;
        if (llama_decode(ctx, llama_batch_get_one(&tok, 1)) != 0) break;
    }
    r.gen_s = secs(t0, clk::now());
    llama_sampler_free(smpl);
    return r;
}

} // namespace

int main(int argc, char **argv) {
    if (argc < 5) {
        std::cerr << "usage: vision-eval <model.gguf> <mmproj.gguf> <tasks.json> <out.jsonl> "
                     "[--describe <text.gguf>] [--max-image-tokens N] [--no-think]\n";
        return 2;
    }
    std::string text_model_path;
    int max_image_tokens = -1;
    bool no_think = false;
    for (int i = 5; i < argc; i++) {
        const std::string a = argv[i];
        if (a == "--describe" && i + 1 < argc) text_model_path = argv[++i];
        else if (a == "--max-image-tokens" && i + 1 < argc) max_image_tokens = std::stoi(argv[++i]);
        else if (a == "--no-think") no_think = true;
    }
    const std::string tasks_path = argv[3];
    const std::string tasks_dir = tasks_path.substr(0, tasks_path.find_last_of('/') + 1);
    std::ifstream tf(tasks_path);
    const json spec = json::parse(tf);

    llama_backend_init();
    auto mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model *model = llama_model_load_from_file(argv[1], mp);
    if (!model) {
        std::cerr << "failed to load model\n";
        return 1;
    }
    const llama_vocab *vocab = llama_model_get_vocab(model);

    auto cp = mtmd_context_params_default();
    cp.use_gpu = false;
    cp.n_threads = N_THREADS;
    cp.print_timings = false;
    cp.warmup = false;
    if (max_image_tokens > 0) cp.image_max_tokens = max_image_tokens;
    mtmd_context *mctx = mtmd_init_from_file(argv[2], model, cp);
    if (!mctx || !mtmd_support_vision(mctx)) {
        std::cerr << "failed to load mmproj\n";
        return 1;
    }

    llama_model *text_model = nullptr;
    if (!text_model_path.empty()) {
        text_model = llama_model_load_from_file(text_model_path.c_str(), mp);
        if (!text_model) {
            std::cerr << "failed to load text model\n";
            return 1;
        }
    }

    std::ofstream out(argv[4]);
    for (const auto &t : spec["tasks"]) {
        const std::string image = t["image"];
        const std::string question = t["question"];
        json row = {{"id", t["id"]}, {"image", image}, {"question", question}};

        std::string image_path = tasks_dir + image;
        auto wrap = mtmd_helper_bitmap_init_from_file(mctx, image_path.c_str(), false, mtmd_helper_init_opt_default());
        if (!wrap.bitmap) {
            row["error"] = "image not found";
            out << row.dump() << "\n";
            out.flush();
            continue;
        }

        std::string system = text_model ? spec["describe_system"].get<std::string>() : spec["system"].get<std::string>();
        if (no_think) system += " /no_think";
        const std::string user = std::string(mtmd_default_marker()) + "\n" +
                                 (text_model ? spec["describe_prompt"].get<std::string>() : question);
        const std::string prompt = format_chat(model, {{"system", system}, {"user", user}});

        llama_context *ctx = new_context(model);
        mtmd_input_chunks *chunks = mtmd_input_chunks_init();
        mtmd_input_text txt = {prompt.c_str(), prompt.size(), true, true};
        const mtmd_bitmap *bmps[] = {wrap.bitmap};
        if (mtmd_tokenize(mctx, chunks, &txt, bmps, 1) != 0) {
            row["error"] = "tokenize failed";
            out << row.dump() << "\n";
            llama_free(ctx);
            continue;
        }

        // Evaluate chunk by chunk to time the image encoder separately.
        llama_pos n_past = 0;
        double encode_s = 0, text_s = 0;
        size_t image_tokens = 0;
        bool ok = true;
        for (size_t i = 0; i < mtmd_input_chunks_size(chunks); i++) {
            const auto *chunk = mtmd_input_chunks_get(chunks, i);
            const bool is_image = mtmd_input_chunk_get_type(chunk) == MTMD_INPUT_CHUNK_TYPE_IMAGE;
            const auto t0 = clk::now();
            const bool last = i + 1 == mtmd_input_chunks_size(chunks);
            if (mtmd_helper_eval_chunk_single(mctx, ctx, chunk, n_past, 0, N_BATCH, last, &n_past) != 0) ok = false;
            const double s = secs(t0, clk::now());
            if (is_image) {
                encode_s += s;
                image_tokens += mtmd_input_chunk_get_n_tokens(chunk);
            } else {
                text_s += s;
            }
            if (!ok) break;
        }
        if (!ok) {
            row["error"] = "eval failed";
            out << row.dump() << "\n";
            llama_free(ctx);
            continue;
        }
        Reply r = generate(ctx, vocab);
        row["image_tokens"] = image_tokens;
        row["image_s"] = encode_s;
        row["text_prompt_s"] = text_s;
        row["generated"] = r.generated;
        row["tok_per_s"] = r.gen_s > 0 ? r.generated / r.gen_s : 0.0;
        row["cut"] = r.cut;
        llama_free(ctx);
        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(wrap.bitmap);

        if (!text_model) {
            row["reply"] = no_think ? strip_think(r.text) : r.text;
        } else {
            // Second step: the text AI answers from the description, as the app would in a Light chat.
            const std::string description = strip_think(r.text);
            row["description"] = description;
            const std::string q = "[" + spec["described_prefix"].get<std::string>() + "\n" + description + "]\n\n" + question;
            const std::string p2 = format_chat(text_model, {{"system", spec["system"].get<std::string>()}, {"user", q}});
            const llama_vocab *tv = llama_model_get_vocab(text_model);
            llama_context *tctx = new_context(text_model);
            std::vector<llama_token> toks(p2.size() + 16);
            const int n = llama_tokenize(tv, p2.c_str(), (int32_t) p2.size(), toks.data(), (int32_t) toks.size(), true, true);
            toks.resize(n > 0 ? n : 0);
            const auto t0 = clk::now();
            for (size_t i = 0; i < toks.size(); i += N_BATCH) {
                const int nb = (int) std::min<size_t>(N_BATCH, toks.size() - i);
                llama_decode(tctx, llama_batch_get_one(toks.data() + i, nb));
            }
            row["answer_prompt_s"] = secs(t0, clk::now());
            Reply a = generate(tctx, tv);
            row["reply"] = a.text;
            row["answer_generated"] = a.generated;
            row["answer_tok_per_s"] = a.gen_s > 0 ? a.generated / a.gen_s : 0.0;
            llama_free(tctx);
        }
        std::cout << row.dump(1) << "\n";
        out << row.dump() << "\n";
        out.flush();
    }
    mtmd_free(mctx);
    if (text_model) llama_model_free(text_model);
    llama_model_free(model);
    return 0;
}
