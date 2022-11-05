# argq.α

## About

(pre-α release, anything is subject to change)

`argq` is a small Clojure library with no dependencies to support uniform argument quoting across platforms, and much more.

## Usage


Include dependency in `deps.edn`

```clojure
ikappaki/argq.alpha {:git/url  "https://github.com/ikappaki/argq.alpha" :sha "..."}
```

Require the library

```clojure
(:require [ikappaki.argq.alpha :as q])
```

At the beginning of your `-main` function, pass the command line arguments sequence to `q/args-parse` and indicate what action to take on parsing error:
```clojure
(defn -main
  [& args]
  (let [args (q/args-parse args :on-error! (fn [error _args]
                                             (println :error (pr-str error))
                                             (System/exit 1)))]
    ;; ...
    ))
```

You can now pass `argq` values to your program arguments, using the `'#ns/tag[opts] element'` syntax. Please note that the argument is wrapped in single quotes `'` for cross platform compatibility.

- Get help about the available tags
``` shell
your-program '#clj/help'
```

- Introducing he `clj/%` tag. Pass a cross platform escaped argument to your program. Escaped characters begin with `%`)
``` shell
your-program '#clj/% %0 this argum%Dnt is %1 double quoted %1 %0'
```

- If you want to know the actual value that will be passed to the program, append the `:v` (for **V**erbose) option to the tag

``` shell
your-program '#clj/%:v I%Qm using the following escaped chars %A %B %C %D %G %I %L %P %Q %S %T %0 %1 %2'
```

- Introducing the `clj/%i` tag. Let `argq` prompt the user for the argument value and print out its escaped counterpart that can be used on the command line in future invocations
``` shell
your-program '#clj/%i please enter a value for this argument'
```


- You can mix arguments of any type in any order

``` shell
your-program "first argument" '#clj/% second %Largument%G' "third argument"
```	

- Introducing the `clj/publish` tag. When you are ready, you can publish your arguments to the world for use on any platform

``` shell
your-program '#clj/publish' "first argument" '#clj/% second %Largument%G' '#clj/%i input third argument'
```

- Clone this code base and give `argq` arguments passing a try
``` shell
[powershell -Command] clojure -M:main-test "first arg" '#clj/% %0quoted arg%0' '#clj/%i input last argument'
```

- You can add new tags in your code by simply defining a new `transform` method. The `argq/file` tag is not part of the spec and has a very simple definition in [test/ikappaki/argq/main.clj](test/ikappaki/argq/main.clj). 
``` shell
[powershell -Command] clojure -M:main-test '#clj/help'
```
``` shell
[powershell -Command] clojure -M:main-test "next argument is read from a file" '#argq/file deps.edn'
```

## %-escape syntax

Escape codes are two character codes starting with `%` where the second character is either a single digit or an upper-case Latin character mnemonic.

| code     | escapes | explain                              | special in     |
|----------|---------|--------------------------------------|----------------|
| %% or %P | %       | **P**er-cent                         | `argq`         |
| %A       | &       | **A**mbersand                        | PS             |
| %B       | \`      | **B**cktick                          | PS             |
| %C       | ^       | **C**aret                            | PS             |
| %D       | $       | **D**ollar                           | unix shell, PS |
| %G       | >       | **G**reater-than                     | PS             |
| %I       | \|      | p**I**pe                             | PS             |
| %L       | <       | **L**ess-than                        | PS             |
| %Q       | '       | single-**Q**uote                     | *              |
| %S       | \       | back**S**lash                        | *              |
| %0       | "       | **0**-times backslashed double quote | *              |
| %1       | \\"     | **1**-time bdq                       | *              |
| %2       | \\\\"   | **2**-times bdq                      | *              |


## Problem

It is most useful for cross-platform compatibility to be able to write command line tools whose arguments can use the same syntax across all platforms, but unfortunately different shells across different platforms treat some characters specially requiring unique syntax handling on each platform.

A good example of such characters is the double quote `"` and its escaped sequences such as `\"` and `\\"`.

## `argq` lib

`argq` is an extensible, dependencies free, library which solves the argument syntax diversity problem by limiting the set of allowable syntax characters to those that have no special meaning in any platform, and escaping everything else.

## Features

- Uniform argument syntax across all platforms.
- Only a single function call is required at the start of your program to enable the functionality.
- Provides single character escape mnemonics that should be easy to remember or guess their meaning mechanically after some use.
- Uses special escape mnemonics, `%1` and `%2`, to facilitate the delineation of single and double escaping of double quotes in arguments.
- Extensible to use the full power of Clojure to transform arguments in any way imaginable, inspired by [edn tagged elements](https://github.com/edn-format/edn#tagged-elements). 
