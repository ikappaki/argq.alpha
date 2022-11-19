# argq.α

## About

([α-status](#α-status), everything is subject to change)

`argq` is a small Clojure library with no dependencies to support uniform argument quoting across platforms, and much more.

## Problem

It is most useful for cross-platform compatibility to be able to write command line tools whose arguments can use the same syntax across all platforms, but unfortunately different shells across different platforms treat some characters specially, requiring using unique syntax handling on each platform that is not applicable to other platforms.

A good example of such characters is the double quote `"` and its escaped sequences such as `\"` and `\\\"`. There is no known common syntax to make escaped double quotes to work both on Unix and MS-Windows shells.

## `argq` lib

`argq` is an extensible, dependencies free, non-intrusive library which solves the argument syntax diversity problem by limiting the set of allowable syntax characters in an argument to those that have no special meaning in any platform, while escaping everything else.

## Features

- Uniform argument syntax across all platforms.
- Inline tools to assist user with escaping and un-escaping of arguments.
- Only a single function call is required at the start of your program to enable the functionality.
- Has no external dependencies and is non-intrusive, only kicks in if an argument matches a known tag.
- Provides single character escape mnemonics that should be easy to remember or guess their meaning mechanically after some use, see [*-escape syntax](#-escape-syntax). 
- Extensible via new tags to use the full power of Clojure to transform arguments in any way imaginable beyond quoting, inspired by the [edn tagged elements](https://github.com/edn-format/edn#tagged-elements).

## Concerns

- Syntax looks alien and might scare people off, while writers should not impose such syntax on people.

_Thoughts_

The library as a minimum can be used to solve a very particular issue which has dare consequences: publishing cross platform command line instructions to the world.

If the instructions do not work as advertised on some platforms of interest, as it is currently the likely case for MS-Windows, then this is most probable to scare newcomers off and confuse the rest of the people, limiting Clojure reach to specific platforms only. The library addresses this issue.

The syntax should not look alien (i.e. incomprehensible) to Clojurians. It uses the familiar tagged literal syntax of `#ns/symbol` to indicate the element which follows should be treated in a special way. Most of the argument value maintains its original form except for a dozen of symbols that are escaped using mnemonic characters, thus the hope is that a user should be able to reason about the argument value without much difficulty.

Furthermore, the library's use is optional and its syntax at the very least is not to be imposed on others who don't use it. It comes with tooling to let authors publish their CLI invocation to the web without the need to know anything about the quoting rules. Users can just copy paste the that command from the web on their shell and it should just work across any platform/shell.

The library offers much more than cross-platform argument compatibility for those that want to opt-in (see [Case studies](#Case-studies) for an example of how to write an extension to invoke sub commands in arguments).

## Setup

Manually, to include lib dependency in `deps.edn`

```clojure
ikappaki/argq.alpha {:git/url  "https://github.com/ikappaki/argq.alpha" :sha "..."}
```

or with [neil](https://github.com/babashka/neil), to add the library to `deps.edn`
```shell
neil add dep ikappaki/argq.alpha --latest-sha
```
or to upgrade to the latest sha
```shell
neil dep upgrade :lib ikappaki/argq.alpha
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

You can now pass `argq` values to your program arguments, using the `"'#ns/tag[opts] element'"` syntax.

## Usage

### *-escape syntax

Escape codes are two character codes starting with `*` where the second character is either a single digit or a Latin character mnemonic.

| code     | escapes | explain                              | special in     |
|----------|---------|--------------------------------------|----------------|
| ** or *a | *       | **a**sterisc                         | `argq`         |
| *A       | &       | **A**mbersand                        | PS             |
| *B       | \`      | **B**acktick                         | PS             |
| *C       | ^       | **C**aret                            | PS             |
| *D       | $       | **D**ollar                           | unix shell, PS |
| *G       | >       | **G**reater-than                     | PS             |
| *I       | \|      | p**I**pe                             | PS             |
| *L       | <       | **L**ess-than                        | PS             |
| *P       | %       | **P**er-cent                         | cmd            |
| *q       | '       | single-**q**uote                     | all            |
| *Q       | "       | double-**Q**uote                     | all            |
| *S       | \       | back**S**lash                        | all            |
| *1       | \\"     | **1**-time  backslashed double quote | all            |

### tags

The lib only kicks in when there is an argument of the form `"'#ns/tag[opts] element'"` and the `ns/tag` is a known tag.

Please note that the argument must be wrapped in a pair of double and single quotes `"'` to maximize cross platform compatibility.

As an example, a command line argument of `"'#clj/esc this is a *Qstring*Q'"` has a `ns/tag` of `clj/esc` and an `element` of `this is a *Qstring*Q`. 

`argq` will dispatch the `element` to the `:clj/esc` handler, which will un-escape it and pass `this is a "string"` to the program as the actual argument value.

Information with examples about each standard tag follows.

#### `clj/help`

Get help about the known tags

``` shell
your-program "'#clj/help'"
```

#### `clj/esc`

Unescape rest of the argument and pass value to program as such.

``` shell
your-program "'#clj/esc *Q this argum*Dnt has *1 double quotes *1 *Q'"
# arg1 => " this argum$nt has \" double quotes \" "
```

If you want to know the actual value that will be passed to the program, append the `:v` (for **V**erbose) option to the tag

``` shell
your-program "'#clj/esc:v I*qm using the following escaped chars *a *A *B *C *D *G *I *L *P *q *Q *S *X *1'"
# arg1 => I'm using the following escaped chars * & ` ^ $ > | < % ' " \ *X \"
#
# stdout => #:ikappaki.argq.alpha{:pos 0,
#                                 :in
#                                 "I*qm using the following escaped chars *a *A *B *C *D *G *I *L *P *q *Q *S *X *1",
#                                 :out
#                                 "I'm using the following escaped chars * & ` ^ $ > | < % ' \" \\ *X \\\""}
```

You can mix arguments of any type in any order

``` shell
your-program "first argument" "'#clj/esc second *Largument*G'" "third argument"
# arg1 => first argument
# arg2 => second <argument>
# arg3 => third argument

```	

#### `clj/prompt`

Let `argq` prompt the user for the argument value and print out its escaped counterpart that can be used on the command line as an escaped argument for future invocations
``` shell
your-program "'#clj/prompt please enter a value for this argument'"
# stdout => Enter value for arg at pos 1, please enter a value for this argument:
# stdin <= "test 100%"
#
# arg1 => "test 100%"
#
# stdout => :input
#             "test 100%"
#
#           :use-this-as-a-safe-command-line-argument-replacement
#             "'#clj/esc *Qtest 100*P*Q'"
```

The user entered a hypothetical `"test 100%" value as the argument input when prompted, and can now replace the program's argument with the suggested quoted value
```shell
your-program "'#clj/esc *Qtest 100*P*Q'"
```

i.e. `your-program` is going to receive `"test 100%"` as the argument value.

#### `clj/publish`

When ready, you can publish your arguments to the world for use on any platform

``` shell
your-program "'#clj/publish'" 'first argument' "second <argument>" "'#clj/prompt enter third argument'"
# stdout => Enter value for arg at pos 4, enter third argument:
# stdin <= "test 100%"
#
# arg1 => first argument
# arg2 => second <argument>
# arg3 => "test 100%"
#
# stdout => :cross-platform-args
#             'first argument' "'#clj/esc second *Largument*G'" "'#clj/esc *Qtest 100*P*Q'"
```

### playground

Clone this codebase and give `argq` arguments passing a try

``` shell
[powershell -Command] clojure -M:main-test "first arg" "'#clj/esc *0quoted arg*0'" "'#clj/prompt enter last argument'"
```

You can add new tags in your code by simply defining a new `transform` method. The `argq/file` tag is not part of the spec and has a very simple definition in [test/ikappaki/argq/main.clj](test/ikappaki/argq/main.clj). 

```clojure
(defmethod q/transform :argq/file
  ;; Returns an `argq` result map of the contents of file at ELEMENT
  ;; path, or an error with INFOrmation about the argument if
  ;; something went wrong.
  ;;
  ;; Return map can have the following keys:
  ;;
  ;; :argq/res the contents of the file.
  ;;
  ;; :argq/err error details.
  [_ element & info]
  (if-not element
    {:argq/err [:argq/file :error :!element info]}

    (try
      {:argq/res (slurp element)}
      (catch Exception e
        {:argq/err [:argq/file :error (pr-str (ex-message e)) info]}))))
```

``` shell
[powershell -Command] clojure -M:main-test "'#clj/help'"
```
``` shell
[powershell -Command] clojure -M:main-test "next argument is read from a file" "'#argq/file deps.edn'"
```

## Case studies

Real world example cases that can benefit from `argq`.

### Clojure CLI Tools

#### 📜 Official doc: Clojure CLI invocation that fails with PowerShell on MS-Windows

https://clojure.org/guides/deps_and_cli#command_line_deps

It invokes `clojure` with a dependency specified on the command line as an edn map

```shell
clojure -Sdeps '{:deps {org.clojure/core.async {:mvn/version "1.5.648"}}}'
```

but this won't work on MS-Windows, because the second argument will be passed in to the program without the quotes, i.e. the version number will be unquoted resulting to an error.

with `argq` the call can be made cross-platform with

```shell
clojure -Sdeps "'#clj/esc {:deps {org.clojure/core.async {:mvn/version *Q1.5.648*Q}}}'"
```

#### 📜 Official doc: Clojure CLI invocation on MS-Windows that comes three different variants depending on the shell it is invoked on

https://github.com/clojure/tools.deps.alpha/wiki/clj-on-Windows

The page lists three different ways to pass a deps map as an argument based on the shell being invoked on MS-Windows

1. PowerShell
   ```ps1
   clj -Sdeps '{:deps {viebel/klipse-repl {:mvn/version ""0.2.3""}}}' -m klipse-repl.main
   ```

2. Command prompt
   ```cmd
   powershell -command clj -Sdeps '{:deps {viebel/klipse-repl {:mvn/version """"""0.2.3""""""}}}' -m klipse-repl.main
   ```

3. Git Bash
	```bash
	powershell -command 'clj -Sdeps "{:deps {viebel/klipse-repl {:mvn/version """"0.2.3""""}}}" -m klipse-repl.main'
	```

while **`argq`** can be called the same across all platforms

```cmd
[powershell -Command] clj -Sdeps "'#clj/esc {:deps {viebel/klipse-repl {:mvn/version *Q0.2.3*Q}}}'" -m klipse-repl.main
```

#### 📜 Ask Clojure: question on how to pass command output as edn string inline argument

https://ask.clojure.org/index.php/11585/convention-bypassing-parsing-reduce-quotes-arguments-passed

The question asks how it might be possible to use command substitution on the Unix shell (i.e. `$()`) to pass the output of a command as an argument to `clojure -X` when the latter only expects and accepts EDN values

```shell
clojure -X:bench :json "$(my_json_producing_cmd --blah)"

```

The above hypothetical `my_json_producing_cmd` command will return a json string which can't be parsed as valid EDN form by `clojure -X`.

A potential solution using `argq`, is to create a new `shell` tag that will run the given command and return its std output as an argument. If it is called with an `:s` option, then the argument value will be returned as an EDN string (i.e. using `pr-str`)

```shell
clojure -X:bench :json "'#argq/shell:s my_json_producing_cmd --blah'"
```

This will also work across all platforms, not just with the Unix shell.

A toy `shell` tag can be trivially written with [babashka.process](https://github.com/babashka/process) as

```clojure
(defmethod q/transform :argq/shell
  ;; Returns an `argq` result map of the stdout of running the ELEMENT
  ;; command, or an error with INFOrmation about the argument if
  ;; something went wrong.
  ;;
  ;; Supports the following kw options in OPT-SET:
  ;;
  ;; :s Convert result to Clojure string.
  ;;
  ;; Return map can have the following keys:
  ;;
  ;; :argq/res the stdout of the command.
  ;;
  ;; :argq/err error details.
  [_ element & {:keys [opts-set] :as info}]
  (if-not element
    {:argq/err [:argq/shell :error :!element info]}

    (try
      {:argq/res (let [{:keys [out]} (-> (p/process element {:out :string})
                                                  p/check)]
                   (if (some #{:s} opts-set)
                     (pr-str out)
                     out))}
      (catch Exception e
        {:argq/err [:argq/shell :error (pr-str (ex-message e)) info]}))))
```

(see [test/ikappaki/argq/main.clj](test/ikappaki/argq/main.clj)).

## α-status

Some of the naming choices are likely to change to facilitate greater acceptance

- Escape codes start with `*`, perhaps other symbols such as `!` or `/` or `?` are more pleasant to the eye?
- The standard tag literals have a namespace of `clj` (e.g. as in `#clj/publish`), this could change to either something shorted (e.g. `q`) or more library specific (e.g. `argq`)?
- The standard tag literals have a long name, such as `#clj/publish`, this can change to something shorter, such as `#clj/pub` or `#clj/p` or aliased as such?


