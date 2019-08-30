(ns jira-auth.jirad
  (:require
   [cljs-node-io.core :as io :refer [slurp spit]]
   [cljs-bean.core :refer [->clj]]
   js-yaml
   os))

(defn read-config []
  (->>
   "/.jira.d/config.yml"
   (str (.homedir os))
   slurp
   js-yaml/load
   ->clj))

(defn save-cookies-js [cookies]
  (println "saving cookies")
  (let [content (js/JSON.stringify cookies)]
    (spit (str (.homedir os) "/.jira.d/cookies.js") content)))


(comment
 (->clj (js/JSON.parse (slurp (str (.homedir os) "/.jira.d/cookies.js")))))
