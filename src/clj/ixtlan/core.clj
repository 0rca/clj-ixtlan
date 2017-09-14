(ns ixtlan.core
  (:require [ring.util.response :refer [file-response]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.edn :refer [wrap-edn-params]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [compojure.core :refer [defroutes GET PUT]]
            [compojure.route :as route]
            [compojure.handler :as handler]))

(defn as-vector [x] (into []
  (line-seq (clojure.java.io/reader x))))

(def translations {:en (as-vector "resources/data/ixtlan-en.txt")
                   :es (as-vector "resources/data/ixtlan-es.txt")
                   :it (as-vector "resources/data/ixtlan-it.txt")
                   :de (as-vector "resources/data/ixtlan-de.txt")})

(def limits {:de 1259
             :en (-> translations :en count dec)
             :it (-> translations :it count dec)
             :es (-> translations :es count dec)})

(defn index []
  (file-response "public/html/index.html" {:root "resources"}))

(defn generate-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (let [limit (limits :en)
               line (if (pos? data) (min data limit) 1)]
           (pr-str {:line  line
                    :total limit
                    :text (into {} (map (fn [[k v]]
                                          [k (if (> data (limits k))
                                               "(No translation available, yet.)"
                                               (if (empty? (v data)) "---" (v data)))]) translations))}))})

(defroutes routes
  (GET "/" [] (index))
  (GET "/line/:id" [id] (generate-response
                          (try (Integer/parseInt id)
                               (catch Exception _ 0))))
  (route/files "/vendor" {:root "bower_components"})
  (route/files "/" {:root "resources/public"}))

(def app
  (-> routes
      wrap-not-modified
      wrap-edn-params))

(defn -main [port]
  (run-jetty app {:port (Integer. port) :join? false}))
