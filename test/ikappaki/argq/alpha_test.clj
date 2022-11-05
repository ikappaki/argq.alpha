(ns ikappaki.argq.alpha-test
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [ikappaki.argq.alpha :as q]))


(deftest string-test

  (testing "escape"
    (is (= "%% %00%11%22%21%10%0"
           (q/str-escape "% \"0\\\"1\\\\\"2\\\\\"1\\\"0\"")))
    (is (= "%% %00%11%22%S%23%S%22%21%10%0"
           (q/str-escape "% \"0\\\"1\\\\\"2\\\\\\\"3\\\\\\\"2\\\\\"1\\\"0\"")))
    (is (= "%0%%a%%d%%9%B"
           (q/str-escape "\"%a%d%9`"))))

  (testing "unescape"
    (is (= "abcdefg" (q/str-unescape "abcdefg")))
    (is (= "" (q/str-unescape "")))
    (is (= " \" \\\" \\\\\" ` ^ $ > < % ' \\ "
           (q/str-unescape " %0 %1 %2 %B %C %D %G %L %P %Q %S ")))
    (is (= "% \"0\\\"1\\\\\"2\\\\\\\"3\\\\\\\"2\\\\\"1\\\"0\""
           (q/str-unescape "%% %00%11%22%S%23%S%22%21%10%0")))
    (is (= "\"%a%d%9`"
           (q/str-unescape "%0%%a%%d%%9%B"))))

  (testing "all ASCII printable roundtrip"
    (let [printable "!\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"]
      (is (= printable
             (-> (q/str-escape printable)
                 q/str-unescape))))))

(deftest arg-test
  (is (= "#clj/% once %% upon a time %0 %S in the west" (q/arg-escape "once % upon a time \" \\ in the west")))
  (is (= {:argq/res "{:help \"he\\\"ll\\\"o\"}"} (q/arg-unescape "#clj/% {:help %0he%1ll%1o%0}"))))

(deftest spare-test
  (is (nil? (q/arg-sparse "abc")))
  (is (nil? (q/arg-sparse "#clj/unknown")))
  (is (= {:tag :clj/help :opts-set #{} :element nil} (q/arg-sparse "#clj/help")))
  (is (= {:tag :clj/% :opts-set #{} :element "123"} (q/arg-sparse "#clj/% 123")))
  (is (= {:tag :clj/% :opts-set #{} :element "123 456"} (q/arg-sparse "#clj/% 123 456")))
  (is (= {:tag :clj/% :opts-set #{:a} :element "123"} (q/arg-sparse "#clj/%:a 123")))
  (is (= {:tag :clj/% :opts-set #{:a :bcd} :element "123"} (q/arg-sparse "#clj/%:a:bcd 123"))))

(deftest tag-process-test

  (is (= {:argq/res "'\"test123\"'" :argq/tag :clj/%} (q/tag-process :clj/% "%Q%0test123%0%Q" {})))

  (let [out (with-out-str
              (q/tag-process :clj/% "%Q%0test123%0%Q" {:opts-set #{:v} :pos 6}))]
    (is (= #:ikappaki.argq.alpha{:pos 6,
	                         :in "%Q%0test123%0%Q",
	                         :out "'\"test123\"'"}
           (edn/read-string out))))

  (let [out (with-out-str
              (q/tag-process :clj/% "%Q%0test123%0%Q" {:opts-set #{:vv} :pos 6}))]
    (is (= #:ikappaki.argq.alpha{:pos 6,
	                         :in "%Q%0test123%0%Q",
                                 :info {:opts-set #{:vv}, :pos 6}
	                         :out "'\"test123\"'"}
           (edn/read-string out)))))

(def corpus
    [{:src "https://clojure.org/guides/deps_and_cli"
      :args ["{:deps {org.clojure/core.async {:mvn/version \"1.5.648\"}}}"
             "(compile %hello)"]}
     {:src "https://github.com/clojure/tools.deps.alpha/wiki/clj-on-Windows"
      :args ["{:deps {viebel/klipse-repl {:mvn/version \"0.2.3\"}}}"]}])

(deftest corpus-test

  (doseq [{:keys [_src args] :as _s} corpus
          arg args]
    (is (= arg
           (-> (q/arg-escape arg)
               q/arg-unescape
               :argq/res)))))


(deftest arg-parse-test
  (testing "#clj/%"
    (is (= {:argq/res "9" :argq/tag :clj/%} (q/arg-parse "#clj/% 9")))
    (is (= {:argq/res "'\"test123\"'" :argq/tag :clj/%} (q/arg-parse "#clj/% %Q%0test123%0%Q")))
    (is (= {:argq/res "{:aliases {:space {:main-opts [\"-e\" \"(spit \\\"%s\\\" (+ 1 2 3))\"]}}}"
            :argq/tag :clj/%}
           (q/arg-parse "#clj/% {:aliases {:space {:main-opts [%0-e%0 %0(spit %1%%s%1 (+ 1 2 3))%0]}}}"))))

  (testing "#clj/% w opt"
    (let [out (with-out-str (q/arg-parse "#clj/%:v 9" {:pos 5}))]
      (is (= #:ikappaki.argq.alpha{:pos 5, :in "9", :out "9"} (edn/read-string out)))))

  (testing "#clj/%i"
    (is (= {:argq/res "'\"1%23'\"" :argq/tag :clj/%i}
           (binding [*in* (java.io.BufferedReader. (java.io.StringReader. "'\"1%23'\"\n"))]
             (q/arg-parse "#clj/%i test%123"))))
    (is (= ["Enter value for arg at pos N/A, test%123: "
            ":input"
            "  testme"
            ""
            ":use-this-as-a-safe-command-line-argument-replacement"
            "  '#clj/% testme'"]
           (str/split-lines
            (with-out-str (binding [*in* (java.io.BufferedReader. (java.io.StringReader. "testme\n"))]
                            (q/arg-parse "#clj/%i test%123"))))))
    (is (= ["Enter value for arg at pos 6, test%123: "
            ":input"
            "  testme"
            ""
            ":use-this-as-a-safe-command-line-argument-replacement"
            "  '#clj/% testme'"]
           (str/split-lines
            (with-out-str (binding [*in* (java.io.BufferedReader. (java.io.StringReader. "testme\n"))]
                            (q/arg-parse "#clj/%i test%123" :pos 5)))))))

  (testing "#clj/%%"
    (is (= {:argq/res "9" :argq/tag :clj/%} (q/arg-parse "#clj/% 9")))
    (is (= {:argq/res "'\"test123\"'" :argq/tag :clj/%} (q/arg-parse "#clj/% %Q%0test123%0%Q")))
    (is (= {:argq/res "{:aliases {:space {:main-opts [\"-e\" \"(spit \\\"%s\\\" (+ 1 2 3))\"]}}}"
            :argq/tag :clj/%}
           (q/arg-parse "#clj/% {:aliases {:space {:main-opts [%0-e%0 %0(spit %1%%s%1 (+ 1 2 3))%0]}}}"))))

  (testing "#clj/help"
    (is (= {:argq/err [:ikappaki.argq.alpha/help :exit] :argq/tag :clj/help} (q/arg-parse "#clj/help")))
    (is (str/starts-with? (str/triml (with-out-str (q/arg-parse "#clj/help")))
                          "Usage as an ARGQ")))

  (testing "publish"
    (is (= {:argq/res "abc" :argq/pub "abc"} (q/arg-parse "abc" {:publish! true})))
    (is (= {:argq/res "a$c" :argq/pub "#clj/% a%Dc" :argq/tag :clj/%} (q/arg-parse "a$c" {:publish! true}))))

  ;;
  )

(defmethod q/transform ::str-reverse
  [_tag element _info]
  {:argq/res (str/reverse element)}
  )

(deftest args-parse-test
  (testing "results"
    (is (= [{:argq/res "123"} {:argq/res "\\\"123\\\"" :argq/tag :clj/%} {:argq/res "`%456`" :argq/tag :clj/%}]
           (q/args-parse ["123" "#clj/% %1123%1" "#clj/% %B%%456%B"])))
    (is (= ["123" "\\\"123\\\"" "`%456`"]
           (q/args-parse ["123" "#clj/% %1123%1" "#clj/% %B%%456%B"]
                         :on-error! #(throw (ex-info "should not happen" {}))))))

  (testing "debug str"
    (let [dbg-str (with-out-str (q/args-parse ["abc" "#clj/%:v %1123%1" "3"]))]
      (is (= #:ikappaki.argq.alpha{:pos 1, :in "%1123%1", :out "\\\"123\\\""}
             (edn/read-string dbg-str)))))

  (testing "input"
    (is (= [{:argq/res "123"} {:argq/res "testme" :argq/tag :clj/%i} {:argq/res "`%456`" :argq/tag :clj/%}]
           (binding [*in* (java.io.BufferedReader. (java.io.StringReader. "testme\n"))]
             (q/args-parse ["123" "#clj/%i hello" "#clj/% %B%%456%B"])))))

  (testing "error termination"
    (is (= [{:argq/res "123"} {:argq/err [:ikappaki.argq.alpha/help :exit] :argq/tag :clj/help}]
           (q/args-parse ["123" "#clj/help" "456"])))
    (is (= [[:ikappaki.argq.alpha/help :exit]
            [{:argq/res "123"} {:argq/err [:ikappaki.argq.alpha/help :exit] :argq/tag :clj/help}]]
           (q/args-parse ["123" "#clj/help" "456"] :on-error! (fn [error args] [error args])))))

  (testing "new tag"
    (binding [q/*tags* (assoc q/*tags*
                              ::str-reverse {:help "Reverse ELEMENT and use this as argument value."})]
      (is (= [{:argq/res "123"} {:argq/res "cba" :argq/tag ::str-reverse} {:argq/res "456"}]
             (q/args-parse ["123" (str "#" (subs (str ::str-reverse) 1) " abc") "456"])))))


  (testing "publishing"
    (is (= [#:argq{:res "123%4<", :tag :clj/%, :pub "#clj/% 123%%4%L"}
	    #:argq{:res "`%456`", :tag :clj/%, :pub "#clj/% %B%%456%B"}]
           (q/args-parse ["#clj/publish" "123%4<" "#clj/% %B%%456%B"])))
    (let [published (with-out-str (q/args-parse ["#clj/publish" "123%4<" "#clj/% %B%%456%B"]))]
      (is (str/includes? published "'#clj/% 123%%4%L' '#clj/% %B%%456%B'")))
    (is (= ["\\\"123\\\"" "`%456`"]
           (q/args-parse ["#clj/publish" "#clj/% %1123%1" "#clj/% %B%%456%B"]
                         :on-error! #(throw (ex-info "should not happen" {})))))))
