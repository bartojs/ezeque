(ns ezeque.core
  (:refer-clojure :exclude [emit])
  (:require [ezeque.ipc :as ipc]))

(def streams {:incoming (atom (repeatedly promise))
              :outgoing (atom (repeatedly promise))
              :logging (atom (repeatedly promise))
              })
  
(defn deliver!
  ([promises event]
     (swap! promises (fn [p] (deliver (first p) event) (drop 1 p))) event)
  ([event]
     (deliver! (:incoming streams) event)))

(defn raise [event]
  (deliver! (:outgoing streams) event))

(defn emit [event] (deliver! (:logging streams) event))

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
(def logstream (partial event-stream (:logging streams)))


(defn start-logging []
(future (dorun (->> (logstream)
                    (filter :log)
                    (map #(format "LOG %1$tFT%1$tT.%1$tL  %2$s" (java.util.Date.) (pr-str %)))
                    (map println)))))


(def zctx (atom nil))

(defn- start-incoming [connects]
  (emit {:site :start-incoming :log (str "start-incoming " (pr-str connects))})
  (future       
     (try 
      (let [sock (ipc/socket @zctx :SUB connects)
            process (fn []
                      (emit {:site :start-incoming :log "incoming process"})
                      (let [s (ipc/recv sock)] 
                        (when-not (or (nil? s) (empty? (.trim s)) (= s ":quit"))
                          (emit {:site :start-incoming  :log (str "recv '" s "'")})
                          (deliver! (read-string s))
                          true)))]
        (try
         (while (process) (print "."))
         (finally (ipc/close sock :SUB connects)))
        (emit {:site :start-incoming :log "start-incoming done"}))
      (catch Exception e (println "Error in socket :SUB " (pr-str connects) e)))))

;;TODO: use polling and a control (inproc?) socket to send stop all
;; (or in/out only) or handle eterm
;; means we could just have a single future.
;; the control socket needs to create/close & send in same thread
;; (inproc needs bind before connect - so might not work)

(defn- start-outgoing [binds]
  (emit {:site :start-outgoing :log (str "start-outgoing " (pr-str binds))})
  (future
    (try
      (let [sock (ipc/socket @zctx :PUB binds)]
        (try
          (doseq [e (outstream :quit)]
            (emit {:site :start-outgoing :log (str "send " (pr-str e))})
            (ipc/send sock (pr-str e)))
          (finally
            (ipc/close sock :PUB binds))
          )
         (emit {:site :start-outgoing :log "start-outgoing done"}))
     (catch Exception e (println "Error in socket :PUB " (pr-str binds) e)))))

(defn stop []
  (ipc/destroy @zctx))

(defn start
  ([] (start ["tcp://*:5555"] ["tcp://localhost:5555"]))
  ([binds connects]
     (start-logging)
     (stop)
     (reset! zctx (ipc/context))
     (when (seq connects) (start-incoming connects))
     (when (seq binds) (start-outgoing binds))))


(comment
  ;; set jna.library.path=/path/to/zmq3.2 in project.clj
  ;; windows need to rename libzmq-v100-mt.dll to zmq.dll 
  
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




