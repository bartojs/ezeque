(ns evstream.core)

(def evpromises (repeatedly promise))
(defn evstream [] (seque evpromises))

;; consumers
(future (do (doseq [e (take 10 (evstream))] (println "stream1: " @e)) (println "stream1 done.")))

(future (doseq [e (filter #(.startsWith (deref %) "bla") (evstream))]
          (print "stream2:" @e)))

;; producers
(take 3 (map #(deliver % "moo") evpromises))               
(map #(deliver %1 (str "bla" %2)) evpromises (range 100))
(deliver (first (filter (complement realized?) evpromises)) "blabbb")