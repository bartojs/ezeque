(ns ezeque.core
  (:require [ezeque.ipc :as ipc]))

(def promises (atom (repeatedly promise)))

(defn deliver! [event]
  (swap! promises (fn [p] (deliver (first p) event) (drop 1 p))) event)

(defn raise [event]
  (ipc/send-out (pr-str event)))

(defn event-stream "create a (blocking) event stream as a lazy-seq"
  ([]
     (map deref (seque @promises)))
  ([terminator]
     (take-while (complement terminator) (event-stream)))
  ([timeout-ms timeout-value]
     (map #(deref % timeout-ms timeout-value) (seque @promises)))
  ([timeout-ms timeout-value terminator]
     (take-while (complement terminator) (event-stream timeout-ms timeout-value)))
  )

(defn start-comms [incoming outgoing]
  (ipc/start (or incoming ["tcp://*:5555"]) (or outgoing ["tcp://localhost:5555"]) deliver!))

(defn stop-comms []
  (ipc/stop))

(comment
  ;; producers
  ;; could hook up producers to zeromq for inter process event-processing.
  
  ;; event consumers each need their own thread 
  ;; can be written using standard seq lib

  ;; with a timeout specified the consumer can get ahead of producer -
  ;; but thats expected - eg nth is allowed too.

  ;; an overall timeout of event processing could be via future-cancel
  
(future (doseq [e (take 10 (event-stream 10000 "x"))] (println "stream1: " e))
        (println "stream1 done."))

(future (doseq [e (take 10 (event-stream 10000 "x" #(= % "bla")))] (println "stream2: " e))
        (println "stream2 done."))

(future (doseq [e (filter #(.startsWith % "bla") (event-stream))] (print "stream3:" e)))

(future (let [x (nth (event-stream) 3)] (print "future nth (4th event):" x)))

(future (doseq [x (event-stream #(= % "bla"))] (print "future term:" x)) (println "future term done."))

)




