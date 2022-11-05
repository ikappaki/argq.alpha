(ns ikappaki.argq.main
  (:require [ikappaki.argq.alpha :as q]))

(defmethod q/transform :argq/file
  [_ element & info]
  (if-not element
    {:argq/err [:argq/file:error :%-!element info]}

    (try
      {:argq/res (slurp element)}
      (catch Exception e
        {:argq/err [:argq/file :error (pr-str (ex-message e)) info]}))))

(defn -main
  [& args]
  (println :args (pr-str args))
  (binding [q/*tags* (assoc q/*tags* :argq/file
                            {:help "Read file at ELEMENT path and use contents as argument value."})]
    (doseq [[idx {:argq/keys [err res] :as _arg}] (map-indexed vector (q/args-parse args))]
      (println :main/arg idx (or err res)))))

(comment
  (-main "123" "#clj/%i help")
  (-main "123" "#argq/file deps.edn")
  ;;
  )

