(defproject ixtlan "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  ; :jvm-opts ^:replace ["-Xmx1g" "-server"]

  :uberjar-name "ixtlan-0.1.0-SNAPSHOT-standalone.jar"
  :min-lein-version "2.0.0"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2199"]
                 [ring/ring-core "1.2.2"]
                 [ring/ring-jetty-adapter "1.2.2"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [om "0.5.3"]
                 [compojure "1.1.6"]
                 [alandipert/storage-atom "1.2.2"]
                 [com.facebook/react "0.9.0.1"]
                 [fogus/ring-edn "0.2.0"]]

  :ring {:handler ixtlan.core/app}

  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]
            [lein-ring "0.8.7"]]

  :source-paths ["src/clj" "src/cljs"]
  :resource-paths ["resources"]
  :cljsbuild {
              :builds [{:id "dev"
                        :source-paths ["src/cljs"]
                        :compiler {
                                   :output-to "resources/public/js/development/main.js"
                                   :output-dir "resources/public/js/development"
                                   :optimizations :none
                                   :source-map true}}
                       {:id "release"
                        :source-paths ["src/cljs"]
                        :compiler {
                                   :output-to "out/release.js"
                                   :optimizations :advanced
                                   :pretty-print false
                                   :externs  ["react/externs/react.js"]
                                   :preamble ["react/react.min.js"]
                                   :closure-warnings {
                                                      :externs-validation :off
                                                      :non-standard-jsdoc :off}
                                   }}]})
