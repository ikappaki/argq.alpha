(ns ikappaki.argq.alpha-test
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [ikappaki.argq.alpha :as q]))


(deftest string-test

  (testing "escape"
    (is (= "** *Q0*111*10*Q"
           (q/str-escape "* \"0\\\"11\\\"0\"")))
    (is (= "** *Q0*11*S3*S1*10*Q"
           (q/str-escape "* \"0\\\"1\\3\\1\\\"0\"")))
    (is (= "*Q**a**d**9*B"
           (q/str-escape "\"*a*d*9`"))))

  (testing "unescape"
    (is (= "abcdefg" (q/str-unescape "abcdefg")))
    (is (= "" (q/str-unescape "")))
    (is (= " \" \\\" & ` ^ $ > | < % ' \\ * * *X"
           (q/str-unescape " *Q *1 *A *B *C *D *G *I *L *P *q *S *a ** *X")))
    (is (= "* \"0\\\"1\\\\\\\"3\\\\\\\"1\\\"0\""
           (q/str-unescape "** *Q0*11*S*S*S*Q3*S*S*S*Q1*10*Q")))
    (is (= "\"*a*d*9`"
           (q/str-unescape "*Q**a**d**9*B"))))

  (testing "all ASCII printable roundtrip"
    (let [printable "!\"#$*&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"]
      (is (= printable
             (-> (q/str-escape printable)
                 q/str-unescape))))))

(deftest arg-test
  (is (= "#clj/esc once ** upon a time *Q *S in the west" (q/arg-escape "once * upon a time \" \\ in the west")))
  (is (= {:argq/res "{:help \"he\\\"ll\\\"o\"}"} (q/arg-unescape "#clj/esc {:help *Qhe*1ll*1o*Q}"))))

(deftest sparse-test
  (is (nil? (q/arg-sparse "abc")))
  (is (nil? (q/arg-sparse "#clj/unknown")))
  (is (= {:tag :clj/help :opts-set #{} :element nil} (q/arg-sparse "#clj/help")))
  (is (= {:tag :clj/esc :opts-set #{} :element "123"} (q/arg-sparse "#clj/esc 123")))
  (is (= {:tag :clj/esc :opts-set #{} :element "123 456"} (q/arg-sparse "#clj/esc 123 456")))
  (is (= {:tag :clj/esc :opts-set #{:a} :element "123"} (q/arg-sparse "#clj/esc:a 123")))
  (is (= {:tag :clj/esc :opts-set #{:a :bcd} :element "123"} (q/arg-sparse "#clj/esc:a:bcd 123"))))

(deftest tag-process-test

  (is (= {:argq/res "'\"test123\"'" :argq/tag :clj/esc} (q/tag-process :clj/esc "*q*Qtest123*Q*q" {})))

  (let [out (with-out-str
              (q/tag-process :clj/esc "*q*Qtest123*Q*q" {:opts-set #{:v} :pos 6}))]
    (is (= #:ikappaki.argq.alpha{:pos 6,
	                         :in "*q*Qtest123*Q*q",
	                         :out "'\"test123\"'"}
           (edn/read-string out))))

  (let [out (with-out-str
              (q/tag-process :clj/esc "*q*Qtest123*Q*q" {:opts-set #{:vv} :pos 6}))]
    (is (= #:ikappaki.argq.alpha{:pos 6,
	                         :in "*q*Qtest123*Q*q",
                                 :info {:opts-set #{:vv}, :pos 6}
	                         :out "'\"test123\"'"}
           (edn/read-string out)))))

(def corpus
    [{:src "https://clojure.org/guides/deps_and_cli"
      :args ["{:deps {org.clojure/core.async {:mvn/version \"1.5.648\"}}}"
             "(compile *hello)"]}
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
  (testing "#clj/esc"
    (is (= {:argq/res "9" :argq/tag :clj/esc} (q/arg-parse "#clj/esc 9")))
    (is (= {:argq/res "'\"test123\"'" :argq/tag :clj/esc} (q/arg-parse "#clj/esc *q*Qtest123*Q*q")))
    (is (= {:argq/res "{:aliases {:space {:main-opts [\"-e\" \"(spit \\\"*s\\\" (+ 1 2 3))\"]}}}"
            :argq/tag :clj/esc}
           (q/arg-parse "#clj/esc {:aliases {:space {:main-opts [*Q-e*Q *Q(spit *1**s*1 (+ 1 2 3))*Q]}}}"))))

  (testing "#clj/esc w opt"
    (let [out (with-out-str (q/arg-parse "#clj/esc:v 9" {:pos 5}))]
      (is (= #:ikappaki.argq.alpha{:pos 5, :in "9", :out "9"} (edn/read-string out)))))

  (testing "#clj/prompt"
    (is (= {:argq/res "'\"1*23'\"" :argq/tag :clj/prompt}
           (binding [*in* (java.io.BufferedReader. (java.io.StringReader. "'\"1*23'\"\n"))]
             (q/arg-parse "#clj/prompt test*123"))))
    (is (= ["Enter value for arg at pos N/A, test*123: "
            ":input"
            "  testme"
            ""
            ":use-this-as-a-safe-command-line-argument-replacement"
            "  \"'#clj/esc testme'\""]
           (str/split-lines
            (with-out-str (binding [*in* (java.io.BufferedReader. (java.io.StringReader. "testme\n"))]
                            (q/arg-parse "#clj/prompt test*123"))))))
    (is (= ["Enter value for arg at pos 6, test*123: "
            ":input"
            "  testme"
            ""
            ":use-this-as-a-safe-command-line-argument-replacement"
            "  \"'#clj/esc testme'\""]
           (str/split-lines
            (with-out-str (binding [*in* (java.io.BufferedReader. (java.io.StringReader. "testme\n"))]
                            (q/arg-parse "#clj/prompt test*123" :pos 5)))))))

  (testing "#clj/**"
    (is (= {:argq/res "9" :argq/tag :clj/esc} (q/arg-parse "#clj/esc 9")))
    (is (= {:argq/res "'\"test123\"'" :argq/tag :clj/esc} (q/arg-parse "#clj/esc *q*Qtest123*Q*q")))
    (is (= {:argq/res "{:aliases {:space {:main-opts [\"-e\" \"(spit \\\"*s\\\" (+ 1 2 3))\"]}}}"
            :argq/tag :clj/esc}
           (q/arg-parse "#clj/esc {:aliases {:space {:main-opts [*Q-e*Q *Q(spit *1**s*1 (+ 1 2 3))*Q]}}}"))))

  (testing "#clj/help"
    (is (= {:argq/err [:ikappaki.argq.alpha/help :exit] :argq/tag :clj/help} (q/arg-parse "#clj/help")))
    (is (str/starts-with? (str/triml (with-out-str (q/arg-parse "#clj/help")))
                          "Usage as an ARGQ")))

  (testing "publish"
    (is (= {:argq/res "abc" :argq/pub "abc"} (q/arg-parse "abc" {:publish! true})))
    (is (= {:argq/res "a$c" :argq/pub "#clj/esc a*Dc" :argq/tag :clj/esc} (q/arg-parse "a$c" {:publish! true}))))

  ;;
  )

(defmethod q/transform ::str-reverse
  [_tag element _info]
  {:argq/res (str/reverse element)}
  )

(deftest args-parse-test
  (testing "results"
    (is (= [{:argq/res "123"} {:argq/res "\\\"123\\\"" :argq/tag :clj/esc} {:argq/res "`*456`" :argq/tag :clj/esc}]
           (q/args-parse ["123" "#clj/esc *1123*1" "#clj/esc *B**456*B"])))
    (is (= ["123" "\\\"123\\\"" "`*456`"]
           (q/args-parse ["123" "#clj/esc *1123*1" "#clj/esc *B**456*B"]
                         :on-error! #(throw (ex-info "should not happen" {}))))))

  (testing "debug str"
    (let [dbg-str (with-out-str (q/args-parse ["abc" "#clj/esc:v *1123*1" "3"]))]
      (is (= #:ikappaki.argq.alpha{:pos 1, :in "*1123*1", :out "\\\"123\\\""}
             (edn/read-string dbg-str)))))

  (testing "input"
    (is (= [{:argq/res "123"} {:argq/res "testme" :argq/tag :clj/prompt} {:argq/res "`*456`" :argq/tag :clj/esc}]
           (binding [*in* (java.io.BufferedReader. (java.io.StringReader. "testme\n"))]
             (q/args-parse ["123" "#clj/prompt hello" "#clj/esc *B**456*B"])))))

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
    (is (= [#:argq{:res "123*4<", :tag :clj/esc, :pub "#clj/esc 123**4*L"}
	    #:argq{:res "`*456`", :tag :clj/esc, :pub "#clj/esc *B**456*B"}]
           (q/args-parse ["#clj/publish" "123*4<" "#clj/esc *B**456*B"])))
    (let [published (with-out-str (q/args-parse ["'#clj/publish'" "123*4<" "'#clj/esc *B**456*B'"]))]
      (is (str/includes? published "\"'#clj/esc 123**4*L'\" \"'#clj/esc *B**456*B'\"")))
    (let [published (with-out-str (q/args-parse ["#clj/publish" "123*4<" "#clj/esc *B**456*B"]))]
      (is (str/includes? published "\"'#clj/esc 123**4*L'\" \"'#clj/esc *B**456*B'\"")))
    (let [published (with-out-str (q/args-parse ["'#clj/publish'" "'123*4<'" "'#clj/esc *B**456*B'"]))]
      (is (str/includes? published "\"'#clj/esc *q123**4*L*q'\" \"'#clj/esc *B**456*B'\"")))
    (is (= ["\\\"123\\\"" "`*456`"]
           (q/args-parse ["#clj/publish" "#clj/esc *1123*1" "#clj/esc *B**456*B"]
                         :on-error! #(throw (ex-info "should not happen" {})))))))
