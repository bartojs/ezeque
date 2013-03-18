(ns ezeque.ipc
  (:refer-clojure :exclude [send])
  (:require [clojure.java.io :as io]) 
  (:import [zmq ZMQ SocketBase])
  )

(def sockettypes {:PUB ZMQ/ZMQ_PUB :SUB ZMQ/ZMQ_SUB :REQ ZMQ/ZMQ_REQ :REP ZMQ/ZMQ_REP}) 
(def socketopts {:SUBSCRIBE ZMQ/ZMQ_SUBSCRIBE})

(defn context [] (ZMQ/zmq_init 1))
(defn destroy [ctx] (when ctx (ZMQ/zmq_term ctx)))

(defn socket [ctx stype addrs & {:as opts}]
  (println "socket")
  (let [sock (ZMQ/zmq_socket ctx (get sockettypes stype))]
    (when (= stype :SUB)
       (ZMQ/zmq_setsockopt sock (:SUBSCRIBE socketopts) (str (:SUBSCRIBE opts))))
    (cond
     (#{:SUB :REQ} stype)
     (doseq [addr addrs] (ZMQ/zmq_connect sock addr))
     (#{:PUB :REP} stype)
     (doseq [addr addrs] (ZMQ/zmq_bind sock addr))
     :else nil)
    sock))

(defn close [sock stype addrs]
  (when sock
    (cond
       (#{:PUB :REP} stype)
          (doseq [addr addrs] (ZMQ/zmq_unbind sock addr))
       (#{:SUB :REQ} stype)
          (doseq [addr addrs] (ZMQ/zmq_disconnect sock addr))
     )
     (ZMQ/zmq_close sock)))

(defn send [sock str]
  (println "send")
  (ZMQ/zmq_send sock ^String str 0)
  (count str))

(defn recv [sock] 
  (println "recv")
  (if-let [msg (ZMQ/zmq_recv sock 0)]
    (String. (.data msg))))


