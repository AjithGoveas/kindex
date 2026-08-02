#pragma once
#include "tree_sitter/api.h"

#ifdef __cplusplus
extern "C" {
#endif

const TSLanguage *tree_sitter_c(void);
const TSLanguage *tree_sitter_c_sharp(void);
const TSLanguage *tree_sitter_cpp(void);
const TSLanguage *tree_sitter_css(void);
const TSLanguage *tree_sitter_go(void);
const TSLanguage *tree_sitter_java(void);
const TSLanguage *tree_sitter_javascript(void);
const TSLanguage *tree_sitter_kotlin(void);
const TSLanguage *tree_sitter_rust(void);

#ifdef __cplusplus
}
#endif