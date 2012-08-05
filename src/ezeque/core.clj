(ns ezeque.core
  (:require [ezeque.ipc :as ipc]))

(def streams {:incoming (atom (repeatedly promise))
              :outgoing (atom (repeatedly promise))})
  
(defn deliver!
  ([promises event]
     (swap! promises (fn [p] (deliver (first p) event) (drop 1 p))) event)
  ([event]
     (deliver! (:incoming streams) event)))

(defn raise [event]
  (deliver! (:outgoing streams) event))

(defn event-stream "create a (blocking) event stream as a lazy-seq"
  ([promises]
     (map deref (seque @promises)))
  ([promises terminator]
     (take-while (complement terminator) (event-stream promises)))
  ([promises timeout-ms timeout-value]
     (map #(deref % timeout-ms timeout-value) (seque @promises)))
  ([promises timeout-ms timeout-value terminator]
     (take-while (complement terminator) (event-stream promises timeout-ms timeout-value))))

(def instream (partial event-stream (:incoming streams)))
(def outstream (partial event-stream (:outgoing streams)))

(def zctx (atom nil))

(defn- start-incoming [connects]
  (println "start-incoming")
   (future
     (try 
      (let [sock (ipc/socket @zctx :SUB connects)
            process (fn []
                      (println "incoming process().")
                      (let [s (ipc/recv sock)] 
                        (when-not (or (nil? s) (empty? (.trim s)) (= s ":quit"))
                          (println (str "RECV incoming: '" s "'"))                              
                          (deliver! (read-string s))
                          true)))]
        (try
         (while (process) (println "RECV listening..."))
         (finally (ipc/close sock :SUB connects))) 
        (println "incoming stopped.")
        )
      (catch Exception e (println "Error in socket :SUB " (pr-str connects) e)))))

(defn- start-outgoing [binds]
  ;; could wait until first send was made before creating a future
  (println "start-outgoing")
  (future
    (try
      (let [sock (ipc/socket @zctx :PUB binds)]
        (try
          (doseq [e (outstream :quit)] ;; blocks
            (ipc/send sock (pr-str e)))
          (finally
            (ipc/close sock :PUB binds))
          )
       (println "outgoing stopped."))
     (catch Exception e (println "Error in socket :PUB " (pr-str binds) e)))))

(defn stop []
  (ipc/destroy @zctx))

(defn start [binds connects]
  ;(stop)
  (reset! zctx (ipc/context))
  (start-incoming connects)
  )


(comment
  ;; producers
  ;; could hook up producers to zeromq for inter process event-processing.
  
  ;; event consumers each need their own thread 
  ;; can be written using standard seq lib

  ;; with a timeout specified the consumer can get ahead of producer -
  ;; but thats expected - eg nth is allowed too.

  ;; an overall timeout of event processing could be via future-cancel
  
(future (doseq [e (take 10 (instream 10000 "x"))] (println "stream1: " e))
        (println "stream1 done."))

(future (doseq [e (take 10 (instream 10000 "x" #(= % "bla")))] (println "stream2: " e))
        (println "stream2 done."))

(future (doseq [e (filter #(.startsWith % "bla") (instream))] (print "stream3:" e)))

(future (let [x (nth (instream) 3)] (print "future nth (4th event):" x)))

(future (doseq [x (instream #(= % "bla"))] (print "future term:" x)) (println "future term done."))

)




