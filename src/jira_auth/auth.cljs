(ns jira-auth.auth
  (:require [clojure.edn :as edn]
            which))

(def exec (.-exec (js/require "child_process")))

(def gpg-command
  (let [gpg2-path (which/sync "gpg2")]
    (str
     gpg2-path " -q --for-your-eyes-only --no-tty -d "
     "resources/creds.gpg")))

(defn creds []
  (->
   (js/Promise.
    (fn [resolve _]
      (print "decrypting username/pass")
      (exec
       gpg-command
       (fn [err stdout stderr]
         (if (or err (seq stderr))
           (do
             (println err)
             (resolve (or err (seq stderr))))
           (do
             (println ".. done")
             (resolve stdout)))))))
   (.then #(edn/read-string %))))
