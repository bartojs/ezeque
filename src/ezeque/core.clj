(ns ezeque.core)

(def promises (atom (repeatedly promise)))
(defn raise! [event]
  (swap! promises (fn [p] (deliver (first p) event) (drop 1 p))) event)

(defn event-stream "create a (blocking) event stream as a lazy-seq"
  ([] (map deref (seque @promises)))
  ([term] (take-while (complement term) (event-stream)))
  ([t v] (map #(deref % t v) (seque @promises)))
  ([t v term] (take-while (complement term) (event-stream t v)))
  )


(comment
  ;; producers
  ;; could hook up producers to zeromq for inter process event-processing.
  
  ;; consumers
  ;; can be written using standard seq lib

  ;; with a timeout specified the consumer can get ahead of producer -
  ;; but thats as expected - nth is allowed too.

  ;; overall timeout could be via future-cancel
  
(future (doseq [e (take 10 (event-stream 10000 "x"))] (println "stream1: " e))
        (println "stream1 done."))

(future (doseq [e (take 10 (event-stream 10000 "x" #(= % "bla")))] (println "stream2: " e))
        (println "stream2 done."))

(future (doseq [e (filter #(.startsWith % "bla") (event-stream))] (print "stream3:" e)))

(future (let [x (nth (event-stream) 3)] (print "future nth (4th event):" x)))

(future (doseq [x (event-stream #(= % "bla"))] (print "future term:" x)) (println "future term done."))

)




