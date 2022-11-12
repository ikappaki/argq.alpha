(ns ikappaki.argq.alpha
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]))

(def ^:dynamic *tags*
  {:clj/help {:help "Display information about the registered NS/TAGs."}
   :clj/esc  {:help (str "Unescape *-codes in ELEMENT and use that as the argument value."
                       " Use the `:v` OPT to display useful information about the conversion.")}
   :clj/prompt {:help (str "Prompt user with ELEMENT to enter a value and use that as the argument value."
                       " Print out the *-escaped value,"
                       " so that it can be retrieved by the user as a command line argument."
                       " The escape codes are:"
                       " ** or *a for * (a-sterisk), *A for & (Ambersand), *B for ` (Backtick), *C for ^ (Caret),"
                       " *D for $ (Dollar), *G for > (Greater-than), *I for | (pIpe), *L for < (Less-than),"
                       " *P for % (Per-cent), *q for ' (single-quote), *Q for \" (double-Quote),"
                       " *S for \\ (backSlash), and *1 for \\\" (1-times backslashed double quote).")}
   ;; :clj/** {:help "Same as `clj/*`, but also print out the *-unescaped value to the user."}
   :clj/publish {:help "Print out the arguments in argq form suitable for use across any platform."}})

(defmulti transform (fn [tag _ & _] tag))

(defn str-unescape
  [string]
  (if-not (str/index-of string "*")
    string
    (let [[unescaped & escaped-parts] (str/split (str/replace string "**" (str/re-quote-replacement "*a")) #"\*")]
      (->> escaped-parts
           (map #(let [f (first %)
                       r (subs % 1)]
                   (case f
                     \1 (str "\\\"" r)
                     \a (str \* r)
                     \A (str \& r)
                     \B (str \` r)
                     \C (str \^ r)
                     \D (str \$ r)
                     \G (str \> r)
                     \I (str \| r)
                     \L (str \< r)
                     \P (str \% r)
                     \q (str \' r)
                     \Q (str \" r)
                     \S (str \\ r)
                     nil nil
                     (str "*" %))))
           (into [unescaped])
           str/join))))

(def escape-codes
  (array-map \* "**"
             "\\\"" "*1"
             \& "*A" \` "*B" \^ "*C" \$ "*D" \> "*G" \| "*I" \< "*L" \% "*P" \' "*q" \" "*Q" \\ "*S"))

(defn str-escape
  [string]
  (reduce (fn [acc [char rep]]
            (if (str/index-of acc char)
              (str/replace acc (str char) (str/re-quote-replacement rep))
              acc))
          string (vec escape-codes)))

(comment
  (str-unescape "***Qtest123*Q**")
  ;;
  )

(def tag-escape "#clj/esc")

(defn arg-escaped?
  [arg]
  (str/starts-with? arg (str tag-escape \ )))

(defn arg-escape
  [arg]
  (str tag-escape \  (str-escape arg)))

(defn arg-unescape
  [arg]
  (if-not (arg-escaped? arg)
    {:argq/err [::arg-unescape :error :!escaped]}
    {:argq/res (str-unescape (subs arg (count (str tag-escape \ ))))}))

(comment
  (arg-unescape (arg-escape "{:deps {org.clojure/core.async {:mvn/version \"1.5.648\"}}}"))
;;
  )

(defmethod transform :clj/help
  [& _]
  (println "\nUsage as an ARGQ command line argument: \"'#ns/tag[opts] element'\""
           "\nTransform ELEMENT to program argument according to NS/TAG and OPTS."
           "\nOPTS can be a set of colon separated words. Universal OPTS:"
           "\n    :v\tPrint out useful information about the argument."
           "\n\nRegistered NS/TAGs:")
  (doseq [[tag {:keys [help]}] (sort *tags*)]
    (println "\n  " (subs (str tag) 1)  "\t" help))
  (flush)
  {:argq/err [::help :exit]})

(comment
  (transform :clj/help nil)
  ;;
  )

(defmethod transform :clj/esc
  [_tag element & info]
  (if element
    {:argq/res (str-unescape element)}
    {:argq/err [:clj/esc :error :!element info]}))

(defmethod transform :clj/prompt
  [_tag element & {:keys [pos publish!] :as _info}]
  (print (str "Enter value for arg at pos " (if-not pos "N/A" (inc pos))
              (when-not (str/blank? element) (str ", " element)) ": "))
  (flush)
  (let [line (read-line)
        eline (str-escape line)
        uneline (str-unescape eline)]
    (println (str \newline :input))
    (println (str "  " uneline \newline))
    (println :use-this-as-a-safe-command-line-argument-replacement)
    (println (str "  \"'#clj/esc " eline "'\"\n"))
    (cond-> {:argq/res uneline}
      publish!
      (assoc :argq/pub (str "#clj/esc " eline)))))

(defmethod transform :clj/**
  [_tag element & {:keys [pos] :as info}]
  (if-not element
    {:argq/err [:clj/** :error :**-!element info]}

    (let [uq (str-unescape element)]
      (pp/pprint #::{:pos pos :in element :out uq})
      {:argq/res (str-unescape element)})))

(defmethod transform :clj/publish
  [_tag _element & {:keys [pos] :as _info}]
  (if-not (= pos 0)
    {:argq/err [:clj/publish :error :publish-!first-arg]}

    {:argq/res "This argument is to be processed externaly by argq."}))

(comment
  (transform :clj/publish nil {})
  (transform :clj/publish nil {:pos 0})
  ;;
  )



(defn arg-sparse
  [arg]
  (let [arg (if-not (and (> (count arg) 1) (str/starts-with? arg "'") (str/ends-with? arg "'"))
              arg
              (subs arg 1 (dec (count arg))))]
    (when (str/starts-with? arg "#")
      (let [arg-1 (subs arg 1)
            keys-str (map #(subs (str %) 1) (keys *tags*))]
        (if-let [tag-str (some #(when (or (= arg-1 (str %))
                                          (str/starts-with? arg-1 (str % \ ))
                                          (str/starts-with? arg-1 (str % \:)))
                                  %)
                               keys-str)]
          (let [tag-w-opts (first (str/split arg-1 #" "))
                tag-opts-set (->> (str/split tag-w-opts #":")
                                  rest
                                  (map keyword)
                                  set)
                element (when (not= tag-w-opts arg-1) (subs arg-1 (count (str tag-w-opts \ ))))]
            {:tag (keyword tag-str) :opts-set tag-opts-set :element element}))))))

(defn tag-process
  [tag element {:keys [pos opts-set] :as info}]

  (let [{:argq/keys [res] :as result} (transform tag element info)]
    (when res
      (cond
        (some #{:v} opts-set)
        (pp/pprint #::{:pos pos :in element :out res})

        (some #{:vv} opts-set)
        (pp/pprint #::{:pos pos :in element :out res :info info})))
    (assoc result :argq/tag tag)))

(comment
  (tag-process :clj/esc "xyz*D" {:pos 1 :opts-set #{:vv}})
  ;;
  )

(defn arg-parse
  [arg & {:keys [publish!] :as info}]
  (if-let [{:keys [tag opts-set element]} (arg-sparse arg)]
    (let [arg (if-not (and (> (count arg) 1) (str/starts-with? arg "'") (str/ends-with? arg "'"))
                arg
                (subs arg 1 (dec (count arg))))
          info (update info :opts-set concat (map keyword opts-set))]
      (cond-> (tag-process tag element info)
        publish!
        (update :argq/pub #(or % arg))))

    (if-not publish!
      {:argq/res arg}

      (let [arg-esc (str-escape arg)]
        (if (= arg arg-esc)
          {:argq/res arg
           :argq/pub arg}

          (arg-parse (str "#clj/esc " arg-esc) info))))))

(comment
  (arg-parse "#clj/esc:v {:aliases {:space {:main-opts [*Q-e*Q *Q(spit *1%s*1 (+ 1 2 3))*Q]}}}" {:pos 5 :opts [1 2 3]})
  (arg-parse "#clj/**:v {:aliases {:space {:main-opts [*Q-e*Q *Q(spit *1%s*1 (+ 1 2 3))*Q]}}}"
             {:pos 5 :opts [1 2 3] :publish! true})
  (arg-parse "#$^34<>"
             {:pos 5 :opts [1 2 3] :publish! true})
  (arg-parse "xyz"
             {:pos 5 :opts [1 2 3] :publish! true})
  (arg-parse "#clj/** {:aliases {:space {:main-opts [*Q-e*Q *Q(spit *1*s*1 (+ 1 2 3))*Q]}}}" {:pos 5})
  (arg-parse "#clj/** {:aliases {:space {:main-opts [*Q-e*Q *Q(spit *1**s*1 (+ 1 2 3))*Q]}}}" {:pos 5})
  (arg-parse "!£^&*()-_+=,.?@~#{}")
  ;;
  )

(defn args-parse
  [args & {:keys [on-error!]}]
  (let [{:keys [args-pre args-pub error]}
        (-> (reduce (fn [{:keys [args-pre args-pub pos] :as acc} arg]
                      (let [{:argq/keys [err pub tag] :as parsed} (arg-parse arg acc)
                            prev (conj args-pre parsed)]
                        (if err
                          (reduced (assoc acc :args-pre prev :args-pub args-pub :pos pos :error err))
                          (let [publishing? (= tag :clj/publish)
                                prev (cond-> prev publishing? (-> rest vec))
                                args-pub (and args-pub (conj args-pub pub))]
                            (cond-> (assoc acc :args-pre prev :args-pub args-pub :pos (inc pos))
                              publishing?
                              (assoc :publish! true :args-pub []))))))
                    {:args-pre [] :pos 0 :error nil} args)
            doall)]
    (when args-pub
      (println :cross-platform-args)
      (print " ")
      (doseq [arg args-pub]
        (if-not (str/starts-with? arg "#clj/esc")
          (print (str \' arg "' "))
          (print (str "\"'" arg "'\" "))))
      (println \newline)
      (flush))
    (if-not on-error!
      args-pre

      (if-not error
        (vec (map :argq/res args-pre))

        (on-error! error args-pre)))))

(comment
  (args-parse ["123" "#clj/** *1123*1" "#clj/esc *B**456*B"])
  (args-parse ["#clj/publish" "123" "456"])
  (args-parse ["#clj/publish" "123$" "345" "#clj/** *1123*1" "#clj/esc *B**456*B"])
  (args-parse ["#clj/publish" "123$" "345" "#clj/prompt xyz" "#clj/** *1123*1" "#clj/esc *B**456*B"])


  ;;
  )

