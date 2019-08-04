(ns jira-auth.core
  (:require [cljs-bean.core :refer [bean ->clj ->js]]
            [cljs-node-io.core :as io :refer [slurp spit]]
            [jira-auth.auth :as auth]
            [promesa.async-cljs :refer-macros [async]]
            [jira-auth.jirad :as jconfig]
            [promesa.core :as p]
            os
            puppeteer))

(defn- add-days
  "Takes a js/Date object and adds number of days to it. If `date` parameter is
  not given, uses today's date. Can take negative number to subtract days."
  ([num-of-days]
   (add-days (js/Date.) num-of-days))
  ([date num-of-days]
   (.setDate date (+ num-of-days (.getDate date)))
   date))

(defonce current-browser (atom nil))
(defonce current-page (atom nil))

(defn launch-browser []
  (p/alet [opts {:headless        false
                 :args            [#_"--start-fullscreen"
                                   "--no-sandbox"
                                   "--disable-setuid-sandbox"
                                   "--window-size=2560,1440"]
                 :userDataDir     "./user_data"
                 :defaultViewport nil
                 :executablePath  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
                 }
           browser (p/await (.launch puppeteer (clj->js opts)))
           page    (p/await (.newPage browser))]
          (reset! current-browser browser)
          (reset! current-page page)))

(defn okta-page? [page]
  (some? (re-find #"dividendfinance.okta.com" (.url page))))

(defn clear-input [page selector]
  (.evaluate
   page
   #(aset (js/document.querySelector %) "value" "")
   selector))

(defn login [page]
  (p/chain
   (auth/creds)
   (fn [{:keys [username password]}]
     (let [uname-sel "input[id=okta-signin-username]"
           pass-sel "input[id=okta-signin-password]"]
       (p/chain
        (.waitFor page ".auth-container")
        #(clear-input page uname-sel)
        #(clear-input page pass-sel)
        #(.type page uname-sel username)
        #(.type page pass-sel password)
        #(.click page ".o-form-input-name-remember")
        #(.click page "#okta-signin-submit"))))))

(defn mfa [page]
  (p/chain
   (.waitFor page ".mfa-verify-push")
   #(.click page "input[type=checkbox][name=autoPush]")
   #(.click page "input[type=checkbox][name=rememberDevice]")
   #(.click page ".button[type=submit")))

(defn retrieve-cookies [page]
  (p/chain
   (.cookies page)
   (fn [cookies]
     (->> cookies
          ->clj
          (filter #(contains? #{"atlassian.xsrf.token" "JSESSIONID"} (:name %)))
          (map
           (fn [{:keys [name value domain path secure session httpOnly]}]
             {:Name       name
              :Value      value
              :Path       path
              :Domain     domain
              :Secure     secure
              :Session    session
              :Expires    (.toISOString (add-days 15))
              :RawExpires ""
              :MaxAge     0
              :HttpOnly   httpOnly
              :Unparsed   nil}))
          ->js))))

(defn get-n-save [page]
  (p/chain
   (retrieve-cookies page)
   jconfig/save-cookies-js))

(defn main []
  (let [site-url (:endpoint (jconfig/read-config))]
    (p/chain
     (launch-browser)
     #(.goto @current-page site-url)
     #(login @current-page)
     ;; TODO: check if mfa needed
     #(.waitFor @current-page 5000)
     #(retrieve-cookies @current-page)
     jconfig/save-cookies-js
     #(.close @current-browser))))

(set! *main-cli-fn* main)

(comment
  (launch-browser)
  (main)

  (let [site-url (:endpoint (jconfig/read-config))]
    (p/chain
     (.goto @current-page site-url)))

  (login @current-page)
  (mfa @current-page)

  (io/slurp "~/.jira.d/cookies.js")

  (p/chain
   (retrieve-cookies @current-page)
   jconfig/save-cookies-js)

  (slurp "/Users/agibragimov/.jira.d/cookies.js")
  (spit (str "/Users/agibragimov/samnahui.txt") "hui"))
