## Note: This library is obsolete. You should use [parinferish](https://github.com/oakes/parinferish) to parse Clojure code.

## Introduction

A Clojure and ClojureScript library that uses tools.reader to parse clojure code into maps that describe each token. The results come in one large map that organizes them by line number. Example use:

```clojure
(code->tags "(+ 1 1)")
; => {1 [{:begin? true, :column 1, :value (+ [1] [1]), :indent 0, :top-level? true, :skip-indent? true} {:delimiter? true, :column 1} {:end? true, :column 2, :next-line-indent 3, :indent 3} {:begin? true, :column 2, :value +, :indent 3, :top-level? false} {:end? true, :column 3} {:begin? true, :column 4, :value 1, :indent 3, :top-level? false} {:end? true, :column 5} {:begin? true, :column 6, :value 1, :indent 5, :top-level? false} {:end? true, :column 7} {:delimiter? true, :column 7} {:end? true, :column 8, :next-line-indent 0} {:end? true, :column 8}]}
```

## Usage

You can include this library in your project dependencies using the version number in the badge above.

## Licensing

All files that originate from this project are dedicated to the public domain. I would love pull requests, and will assume that they are also dedicated to the public domain.
