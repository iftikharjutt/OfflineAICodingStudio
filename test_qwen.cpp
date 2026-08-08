#include "llama.h"
#include <iostream>
#include <vector>
#include <string>
#include <cstring>

int main(int argc, char** argv) {
    if (argc < 2) {
        std::cerr << "Usage: " << argv[0] << " <model_path>" << std::endl;
        return 1;
    }
    llama_backend_init();
    
    llama_model_params mp = llama_model_default_params();
    llama_model* model = llama_model_load_from_file(argv[1], mp);
    if (!model) {
        std::cerr << "Failed to load model" << std::endl;
        return 1;
    }

    std::string prompt1 = "<|im_start|>assistant\n";
    std::string prompt2 = "<|im_start|>assistant";

    auto print_tokens = [&](const std::string& p) {
        std::vector<llama_token> toks(p.size() + 10);
        int got = llama_tokenize(llama_model_get_vocab(model), p.c_str(), p.size(), toks.data(), toks.size(), false, true);
        if (got < 0) {
            std::cerr << "tokenize failed" << std::endl;
            return;
        }
        std::cout << "Tokens for '" << p << "': ";
        for (int i = 0; i < got; i++) {
            std::cout << toks[i] << " ";
        }
        std::cout << std::endl;
    };

    print_tokens(prompt1);
    print_tokens(prompt2);

    llama_model_free(model);
    return 0;
}
