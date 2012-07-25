(ns evstream.core)

(def promises (atom (repeatedly promise)))
(defn raise! [event]
  (swap! promises (fn [p] (deliver (first p) event) (drop 1 p))) event)
(defn event-stream [] (seque @promises))

;; consumers
(future (do (doseq [e (take 10 (evstream))]
              (println "stream1: " @e))
            (println "stream1 done.")))

(future (doseq [e (filter #(.startsWith (deref %) "bla") (evstream))]
          (print "stream2:" @e)))


;; producers
;(map #(deliver %1 (str "bla" %2)) evpromises (range 100))
;(deliver (first (filter (complement realized?) evpromises)) "blabbb")

;(defn event-stream [timeout-ms timeout-val terminator] (map #(deref % timeout-ms timeout-val) (take-while (comp terminator) (seque @promises))))