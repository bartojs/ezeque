(ns evstream.core)

(def promises (atom (repeatedly promise)))
(defn raise! [event]
  (swap! promises (fn [p] (deliver (first p) event) (drop 1 p))) event)

(defn event-stream "create a (blocking) event stream as a lazy-seq"
  ([] (map deref (seque @promises)))
  ([term] (take-while (complement term) (event-stream)))
  ([t v] (map #(deref % t v) (seque @promises)))
  ([t v term] (take-while (complement term) (event-stream t v)))
  ;([terminator] (take-while terminator (event-stream)))
  ;([timeout-ms timeout-val term] (event-stream #(term (deref % timeout-ms timeout-val))))
  )

;; consumers

;; with a timeout specified the consumer can get ahead of producer -
;; but thats as expected - nth is allowed too.

(future (do (doseq [e (take 10 (event-stream 10000 "x"))]
              (println "stream1: " e))
            (println "stream1 done.")))

(future (do (doseq [e (take 10 (event-stream 10000 "x" #(= % "bla")))]
              (println "stream2: " e))
            (println "stream2 done.")))

(future (doseq [e (filter #(.startsWith % "bla") (event-stream))]
          (print "stream3:" e)))

(future (let [x (nth (event-stream) 3)] (print "future nth (4th event):" x)))

(future (doseq [x (event-stream #(= % "bla"))] (print "future term:" x)) (println "future term done."))

;; overall timeout could be via future-cancel




;; producers
;(map #(deliver %1 (str "bla" %2)) evpromises (range 100))
;(deliver (first (filter (complement realized?) evpromises)) "blabbb")

;(defn event-stream [timeout-ms timeout-val terminator] (map #(deref % timeout-ms timeout-val) (take-while (comp terminator) (seque @promises))))