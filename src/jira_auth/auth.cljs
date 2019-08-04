(ns jira-auth.auth
  (:require [clojure.edn :as edn]))

(def exec (.-exec (js/require "child_process")))

(def gpg-command
  (str
   "$(which gpg2) -q --for-your-eyes-only --no-tty -d "
   "resources/creds.gpg"))

(defn creds []
  (->
   (js/Promise.
    (fn [resolve _]
      (exec
       gpg-command
       (fn [err stdout stderr]
         (if (or err (seq stderr))
           (do
             (println err)
             (resolve (or err (seq stderr))))
           (resolve stdout))))))
   (.then #(edn/read-string %))))
