(ns ixtlan.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.reader :as reader]
            [goog.events :as events]
            [goog.dom :as gdom]
            [goog.style :as gstyle]
            [cljs.core.async :as async :refer [>! <! put! chan alts!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [alandipert.storage-atom :refer [local-storage]]
            )
  (:import [goog.net XhrIo]
           [goog.net EventType]
           [goog History]
           [goog.events KeyHandler]
           [goog.events.KeyHandler EventType]
           [goog.events KeyCodes]
           [goog.history Event]
           )
  )

(enable-console-print!)

(def initial-state {:data {:text {:en "loading..."
                                  :es "descargando..."
                                  :it "caricamento..."
                                  :de "verladung..."}
                           :line 0}
                    :dicts {
                            :en [{:title "Look up in TFD" :url "http://en.tfd.com/$q"}
                                 {:title "Look up in Yandex" :url "http://slovari.yandex.ru/$q/en-ru/#lingvo/"}
                                 {:title "Listen on Forvo" :url "http://forvo.com/word/$q"}
                                 ]

                            :es [{:title "Look up in TFD" :url "http://es.tfd.com/$q"}
                                 {:title "Look up in Yandex" :url "http://slovari.yandex.ru/$q/es-ru/#lingvo/"}
                                 {:title "Listen on Forvo" :url "http://forvo.com/word/$q"}
                                 ]
                            :it [{:title "Look up in TFD" :url "http://it.tfd.com/$q"}
                                 {:title "Look up in Yandex" :url "http://slovari.yandex.ru/$q/it-ru/#lingvo/"}
                                 {:title "Listen on Forvo" :url "http://forvo.com/word/$q"}
                                 ]

                            :de [{:title "Look up in TFD" :url "http://de.tfd.com/$q"}
                                 {:title "Look up in Yandex" :url "http://slovari.yandex.ru/$q/de-ru/#lingvo/"}
                                 {:title "Listen on Forvo" :url "http://forvo.com/word/$q"}
                                 ]
                            }
                    :alert-close-messages ["dismiss"
                                           "got it"
                                           "roger that"
                                           "gotcha!"
                                           "x"
                                           "close"
                                           "thanks"
                                           "uh-huh"
                                           ]
                    :help-alerts [{:visible true
                                   :text "Use keyboard arrows, j,k or space to navigate"}
                                  {:visible true
                                   :text "Click words for more options"}
                                  {:visible true
                                   :text "Toggle languages in upper left menu"}
                                  {:visible true
                                   :text "Use may close these tips by clicking on the rightmost word, right there ---->"}]
                    :settings {:en {:visible true
                                    :titles {true  "Hide English"
                                             false "Show Enlish"}}
                               :es {:visible true
                                    :titles {true "Hide Spanish"
                                             false "Show Spanish"} }
                               :it {:visible true
                                    :titles {true "Hide Italian"
                                             false "Show Italian"} }

                               :de {:visible true
                                    :titles {true "Hide German"
                                             false "Show German"}}}})

; (def app-state (atom initial-state))
(def app-state (local-storage (atom initial-state) :app-state))
(def cache (local-storage (atom {}) :cache))

(def ^:private meths
  {:get "GET"
   :put "PUT"
   :post "POST"
   :delete "DELETE"})

(defn edn-xhr [{:keys [method url data on-success]}]
  (let [xhr (XhrIo.)]
    (events/listen xhr goog.net.EventType.SUCCESS
                   (fn [e]
                     (on-success (reader/read-string (.getResponseText xhr)))))
    (. xhr
       (send url (meths method) (when data (pr-str data))
             #js {"Content-Type" "application/edn"}))))

(defn depunctualize [word]
  ; (let [first-part (-> word clojure.string/lower-case (clojure.string/split #"'") first)]
  (clojure.string/replace word #"[\u00bf,.:;\"?!-]" ""))
  ; )

(defn listen [el type]
  (let [out (chan)]
    (events/listen el type #(put! out %))
    out))

(defn menu-item [data owner]
  (reify
    om/IRender
    (render [_]
      (dom/li nil
              (dom/a #js {:href (:url data)
                          :target "_dict"} (:text data))))))

(defn make-menu [lang word]
  (when (and lang word)
    (map (fn [{:keys [title url]}]
           {:text title
            :url  (clojure.string/replace url #"\$q" (depunctualize word))})
         (-> @app-state :dicts lang))))

(defn menu-view [app owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [clicks (om/get-shared owner [:chans :word-clicks])]
        (go (while true
              (let [[x y w h word lang :as event] (<! clicks)]
                (om/set-state! owner {:x x :y y :w w :h h :lang lang :word word})
                ))))
      )
    om/IRenderState
    (render-state [_ {:keys [x y lang word]}]
      (let [menu-items (make-menu lang word)]
        (apply dom/ul #js {:className "f-dropdown"}
               (om/build-all menu-item menu-items)
               )))
    om/IDidUpdate
    (did-update [_ _ _]
      (let [{:keys [x y w h]} (om/get-state owner)]
        (gstyle/setPageOffset (om/get-node owner) x (+ y h))))
    ))

(defn word-view [word owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [lang]}]
      (dom/a #js {:onClick #(let [clicks (om/get-shared owner [:chans :word-clicks])
                                  node (om/get-node owner)
                                  x (gstyle/getPageOffsetLeft node)
                                  y (gstyle/getPageOffsetTop  node)
                                  size (gstyle/getBorderBoxSize node)]
                              (do
                                (put! clicks [x y (.-width size) (.-height size) word lang ])
                                false)
                              )}
             (str word " ")))))

(defn line-panel-view [line owner]
  (reify
    om/IRenderState
    (render-state [_ state]
      (dom/div #js {:className "row"}
               (apply dom/div #js {:className "small-12 medium-12 large-12 columns panel"}
                      (let [words (clojure.string.split line #"\s")]
                        (om/build-all word-view words {:init-state state})
                        ))))))

(defn button [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [msg label]}]
      (let [keys-chan (om/get-shared owner [:chans :keys])]
      (dom/a #js
             {:className "small-4 medium-4 large-4 columns button"
              :onClick #(put! keys-chan msg)
              }
             label)))))

(defn render-ascii-button [js-stuff txt]
  (let [txt-len (count txt)
        width   (+ txt-len 6)
        vert-side (apply str (take width (repeat "-")))]
    (dom/pre js-stuff (str vert-side "\n|  " txt "  |\n" vert-side))))

(defn ascii-button [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [msg label]}]
      (let [keys-chan (om/get-shared owner [:chans :keys])]
      (render-ascii-button #js {:onClick #(put! keys-chan msg)} label))))
  )

(defn language-toggle-button [settings owner]
  (reify
    om/IRender
    (render [_]
      (dom/li nil
              (dom/div #js {:className "button"
                            :onClick #(om/transact! settings :visible not) }
                       ((settings :titles) (settings :visible))
                       )))))

(defn alert-box [message owner]
  (reify
    ; om/IInitState
    ; (init-state [_] {:close-msg (rand-nth (@app-state :alert-close-messages))})
    om/IRenderState
    (render-state [_ {:keys [close-msg]}]
      (dom/div #js {:className "alert-box info"
                    :data-alert ""}
               (:text message)
               (dom/a #js {:className "close"
                           :onClick #(do
                                       (om/update! message :visible false)
                                       nil)}
                      close-msg))
      )))

(defn clear-cache-button [_ owner]
  (reify
    om/IRender
    (render [_]
      (dom/li nil
              (dom/div #js {:className "button"
                            :onClick #(do
                                        (reset! cache {})
                                        (om/refresh! owner))}
                      (str "Clear cache (" (count @cache) " items)"))))))

(defn reset-settings-button [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/li nil
              (dom/div #js {:className "button"
                            :onClick #(put! (om/get-shared owner [:chans :keys]) :reset)}
                       "Reset settings")))))

(defprotocol IBrowsable
  (-next! [this])
  (-prev! [this]))

(defn -api-xhr! [id data-chan]
  (edn-xhr  {:method :get
             :url  (str "line/" id)
             :on-success #(put! data-chan %)
             }))

(defn app-view [app owner]
  (reify
    IBrowsable
    (-next! [this]
      (aset js/location "hash" (inc (get-in @app [:data :line]))))
    (-prev! [this]
      (aset js/location "hash" (dec (get-in @app [:data :line]))))

    om/IWillMount
    (will-mount [this]
      (let [data-chan (om/get-shared owner [:chans :data])]
        (go (while true
              (let [data (<! data-chan)]
                (om/update! app :loading false)
                (om/update! app :data data)))))
      (let [nav (om/get-shared owner [:chans :navigation])]
        (go (loop [e (.-hash js/location)]
              (let [line (or (second (first (re-seq #"#(\d+)" e)))
                             0)]
                (om/update! app :loading true)
                (-api-xhr! line (om/get-shared owner [:chans :data])))
              (recur (<! nav))))
        )
      (let [chan (om/get-shared owner [:chans :word-clicks])]
        (events/listen js/window "click" (fn [] (put! chan [-99999 0]))))
      (let [key-chan (om/get-shared owner [:chans :keys])]
        (go (loop [k ::none]
              (condp = k
                :next (-next! this)
                :prev (-prev! this)
                :reset (om/update! app (merge initial-state {:data (@app :data)}))
                nil)
              (recur (<! key-chan))))))

    om/IRender
    (render [_]
      (dom/div #js {:className "off-canvas-wrap"}
               (dom/div #js {:className "inner-wrap"}
                        (dom/nav #js {:className "tab-bar"}
                                 (dom/section #js {:className "left-small"}
                                              (dom/a #js {:className "left-off-canvas-toggle menu-icon"}
                                                     (dom/span nil)))
                                 (dom/section #js {:className "middle tab-bar-section"}
                                              (dom/h1 #js {:className "title"} "Voyage to Ixtlan (C.Castaneda)"))
                                 (dom/section #js {:className "right-small"}
                                              (dom/span nil
                                                        (if (:loading app) "[ w ]" ""))
                                              )
                                 )
                        (dom/aside #js {:className "left-off-canvas-menu"}
                                   (apply dom/ul #js {:className "off-canvas-list"}
                                          (dom/li nil (dom/label nil "Settings"))
                                          (dom/br nil)
                                          (concat
                                            (->> app :settings keys (map (fn [lang]
                                                                           (om/build language-toggle-button (-> app :settings lang))
                                                                           )))
                                            [(dom/br nil)
                                             (om/build clear-cache-button app)
                                             (om/build reset-settings-button app)])
                                          )
                                   )
                        (dom/section #js {:className "main-section"}
                                     (dom/div #js {:className "row"}
                                              (om/build button app {:init-state {:msg :prev :label "<<" }})
                                              (dom/div #js {:className "small-4 medium-4 large-4 columns button"}
                                                       (if (-> app :data :total)
                                                         (let [line    (-> app :data :line)
                                                               total   (-> app :data :total)
                                                               percent (* 100 (/ line total))]
                                                           (str line " of " total
                                                                ))
                                                         ))
                                              (om/build button app {:init-state {:msg :next :label ">>"}}))

                                     (apply dom/div #js {:className "row"}
                                            (map #(om/build alert-box % {:init-state {:close-msg (rand-nth (app :alert-close-messages))}})
                                                 (filter :visible (:help-alerts app))))

                                     (apply dom/div #js {:className "translations"}
                                            (->> app :data :text keys (map (fn [lang]
                                                                             (when (get-in app [:settings lang :visible])
                                                                               (om/build line-panel-view
                                                                                         (-> app :data :text lang)
                                                                                         {:init-state {:lang lang}}))
                                                                             ))))
                                     )
                        (om/build menu-view app {:init-state {:x -99999 :y 0}})
                        (dom/a #js {:className "exit-off-canvas"})

                        )))))

(defn navigation-events []
  (let [h (History.)
        out (chan)]
    (events/listen h
                   goog.history.EventType.NAVIGATE
                   #(put! out (str "#" (.-token %)))
                   (doto h (.setEnabled true)))
    out))

(defn key-events []
  (let [out (chan)]
    (events/listen js/window "keyup"
                   (fn [e]
                     (cond
                      (#{37 ; left
                         38 ; up
                         75 ; k
                         } (.-keyCode e))
                      (put! out :prev)
                      (#{32 ; space
                         39 ; right
                         40 ; down
                         74 ; j
                         } (.-keyCode e))
                      (put! out :next)
                      :else (put! out e)
                      )))

    out))

(om/root app-view app-state
         {:shared {:chans {:word-clicks (chan)
                           :navigation (navigation-events)
                           :keys (key-events)
                           :data (chan (async/sliding-buffer 1)) }}
          :target (gdom/getElement "app")})


; (if (empty? (.-hash js/location))
;   (aset js/location "hash" (-> @app-state :data :line)))
