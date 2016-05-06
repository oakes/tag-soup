[![Clojars Project](https://img.shields.io/clojars/v/tag-soup.svg)](https://clojars.org/tag-soup)

## Introduction

A Clojure and ClojureScript library that uses tools.reader to parse clojure code into a list of maps describing each token. Example use:

```clojure
(code->tags "(+ 1 1)")
; => ({:line 1, :column 1, :value (+ [1] [1]), :indent 0, :skip-indent? true} {:line 1, :column 1, :delimiter? true} {:end-line 1, :end-column 2, :next-line-indent 3, :indent 3} {:line 1, :column 2, :value +, :indent 3} {:end-line 1, :end-column 3, :end-tag? true} {:line 1, :column 4, :value 1, :indent 3} {:end-line 1, :end-column 5, :end-tag? true} {:line 1, :column 6, :value 1, :indent 5} {:end-line 1, :end-column 7, :end-tag? true} {:line 1, :column 7, :delimiter? true} {:end-line 1, :end-column 8, :next-line-indent 0} {:end-line 1, :end-column 8, :end-tag? true})
```

## Licensing

All files that originate from this project are dedicated to the public domain. I would love pull requests, and will assume that they are also dedicated to the public domain.
