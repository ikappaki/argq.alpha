(ns ikappaki.argq.main
  (:require [babashka.process :as p]
            [ikappaki.argq.alpha :as q]))

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

(defn main
  [args]
  (println :args (pr-str args))
  (binding [q/*tags* (assoc q/*tags*
                            :argq/file {:help "Read file at ELEMENT path and use contents as argument value."}
                            :argq/shell
                            {:help
                             (str "Invoke ELEMENT as a shell command and return its output as argument value."
                                  " Use the `:s` OPT to convert the return value as a Clojure string."
                                  )})]
    (doseq [[idx {:argq/keys [err res] :as _arg}] (map-indexed vector (q/args-parse args))]
      (println :main/arg idx (or err res)))))

(defn -main
  [& args]
  (main args)
  (shutdown-agents))

(comment
  (main ["123" "#clj/prompt help"])
  (main ["123" "#argq/file deps.edn"])
  (main ["123" "#argq/shell dir"])
  (main ["123" "#argq/shell:s dir"])
  (main ["123" "#argq/shell:s:vv dir"])
  ;;
  )

