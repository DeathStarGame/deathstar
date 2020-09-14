(ns deathstar.extension.main
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string :refer [format]]
   [goog.string.format]
   [clojure.string :as string]
   [cljs.reader :refer [read-string]]
   [clojure.pprint :refer [pprint]]

   [cljctools.cljc.core :as cljc]

   [cljctools.vscode.spec :as host.spec]
   [cljctools.vscode.chan :as host.chan]
   [cljctools.vscode.impl :as host.impl]

   [cljctools.net.socket.spec :as socket.spec]
   [cljctools.net.socket.chan :as socket.chan]
   [cljctools.net.socket.impl :as socket.impl]

   [cljctools.csp.op.spec :as op.spec]

   [deathstar.extension.http-chan.impl :as http-chan.impl]
   [deathstar.extension.http-chan.chan :as http-chan.chan]

   [deathstar.hub.tap.remote.spec :as tap.remote.spec]
   [deathstar.hub.tap.remote.impl :as tap.remote.impl]
  ;;  [deathstar.hub.remote.chan :as hub.remote.chan]
  ;;  [deathstar.hub.remote.impl :as hub.remote.impl]

   [deathstar.user.spec :as user.spec]
   [deathstar.hub.chan :as hub.chan]

   [deathstar.server.spec :as server.spec]

   [deathstar.extension.spec :as extension.spec]
   [deathstar.extension.chan :as extension.chan]

   [deathstar.extension.gui.chan :as extension.gui.chan]

   #_[cljctools.pad.cljsjs1]
   [cljctools.pad.async1]))

(def state (atom
            (apply merge
                   [#::{:gui-tab nil}])))

(def ^:dynamic *workspaceFolder* nil)

(defn state->server-config
  [state]
  (let [{:keys [::extension.spec/servers
                ::extension.spec/connect-to-server]} state]
    (get servers connect-to-server)))

(defn state->socket-url
  [state]
  (let [server-config (state->server-config state)
        {:keys [::server.spec/host
                ::server.spec/port]} server-config]
    (format "ws://%s:%s/ws" host port)))


(def state-remote (tap.remote.impl/create-state))

(add-watch state-remote ::watcher
           (fn [key atom old-state new-state]
             (println (with-out-str (pprint new-state)))))

(def channels (as-> nil chs
                (merge
                 (host.chan/create-channels)
                 (extension.chan/create-channels)
                 (extension.gui.chan/create-channels)
                 (socket.chan/create-channels)
                 (http-chan.chan/create-channels)
                 (hub.chan/create-channels))))

(pipe (::extension.gui.chan/ops| channels) (::host.chan/tab-send| channels))
(pipe (::socket.chan/recv| channels) (::hub.chan/response| channels))

(pipe (tap (::hub.chan/ops| channels) explicit-itercept-of-out|)  (::http-chan.chan/request| channels))

(defn ^:export main [& args]
  (println ::main))

(def exports #js {:activate (fn [context]
                              (println ::activate)
                              (js/Promise.
                               (fn [resolve _]
                                 (go
                                   (<! (host.chan/op
                                        {::op.spec/op-key ::host.chan/extension-activate
                                         ::op.spec/op-type ::op.spec/request}
                                        channels
                                        context))
                                   (host.impl/register-commands
                                    {::host.spec/cmd-ids extension.spec/cmd-ids
                                     ::host.impl/vscode host.impl/vscode
                                     ::host.impl/context host.impl/*context*
                                     ::host.impl/on-cmd (fn [cmd-id #_args]
                                                          (prn ::cmd cmd-id)
                                                          (host.chan/op
                                                           {::op.spec/op-key ::host.chan/cmd}
                                                           (::host.chan/cmd| channels)
                                                           cmd-id))})
                                   (resolve))))
                              #_(js/Promise.
                                 (fn [resolve _]
                                   (go
                                     (<! (host.chan/op
                                          {::op.spec/op-key ::host.chan/extension-activate
                                           ::op.spec/op-type ::op.spec/request}
                                          channels
                                          context))
                                     (<! (host.chan/op
                                          {::op.spec/op-key ::host.chan/register-commands
                                           ::op.spec/op-type ::op.spec/request}
                                          channels
                                          extension.spec/cmd-ids))
                                     (resolve)))))
                  :deactivate (fn []
                                (println ::deactivate)
                                (host.chan/op
                                 {::op.spec/op-key ::host.chan/extension-deactivate}
                                 channels))})
(when (exists? js/module)
  (set! js/module.exports exports))

#_(defn reload
    []
    (.log js/console "Reloading...")
    (js-delete js/require.cache (js/require.resolve "./main")))

(def host (host.impl/create-proc-ops channels {}))

(def socket (socket.impl/create-proc-ops channels {}))

(def http-chan-for-hub (http-chan.impl/create-proc-ops
                        channels
                        {::http-chan.impl/connect-opts (fn []
                                                         (let [{:keys [::server.spec/host
                                                                       ::server.spec/port
                                                                       ::server.spec/http-chan-path]} (state->server-config @state)]
                                                           {::http-chan.impl/host host
                                                            ::http-chan.impl/port port
                                                            ::http-chan.impl/path http-chan-path}))}))

(comment

  (socket.chan/op
   {::op.spec/op-key ::socket.chan/connect}
   channels
   {::socket.spec/url "ws://localhost:8080/ws"})

  (socket.chan/op
   {::op.spec/op-key ::socket.chan/disconnect}
   channels)

  ;;
  )

(def tap-remote (tap.remote.impl/create-proc-ops channels state-remote))

(comment

  (hub.chan/op
   {::op.spec/op-key ::hub.chan/user-join
    ::op.spec/op-type ::op.spec/request}
   channels
   {::user.spec/uuid (cljc/rand-uuid)})

  (hub.chan/op
   {::op.spec/op-key ::hub.chan/list-users
    ::op.spec/op-type ::op.spec/request}
   channels)

  ;;
  )



(defn create-proc-ops
  [channels state]
  (let [{:keys [::extension.chan/ops|
                ::http-chan.chan/request|
                ::host.chan/cmd|m
                ::host.chan/tab-evt|m]
         socket-evt|m ::socket.chan/evt|m
         host-evt|m ::host.chan/evt|m} channels
        cmd|t (tap cmd|m (chan 10))
        relevant-socket-evt? (fn [v]  (#{::socket.chan/connected ::socket.chan/closed} (::op.spec/op-key v)))
        socket-evt|t (tap socket-evt|m (chan 10 (comp (filter (every-pred relevant-socket-evt?)))))
        relevant-host-evt? (fn [v]  (#{::host.chan/extension-activate ::host.chan/extension-deactivate} (::op.spec/op-key v)))
        host-evt|t (tap host-evt|m (chan 10 (comp (filter (every-pred relevant-host-evt?)))))
        relevant-tab-evt? (fn [v]  (#{::host.chan/tab-disposed} (::op.spec/op-key v)))
        tab-evt|t (tap tab-evt|m (chan 10 (comp (filter (every-pred relevant-tab-evt?)))))]
    (go
      (loop []
        (when-let [[v port] (alts! [ops| host-evt|t cmd|t socket-evt|t])]
          (do (println ::value v))
          (condp = port
            host-evt|t
            (condp = (select-keys v [::op.spec/op-key ::op.spec/op-type])

              {::op.spec/op-key ::host.chan/extension-activate
               ::op.spec/op-type ::op.spec/request}
              (let [workspaceFolder (<! (host.impl/select-workspaceFolder {}))
                    deathstar-edn (as-> nil x
                                    (<! (host.impl/read-workspaceFolder-file
                                         workspaceFolder
                                         "deathstar.edn"))
                                    (when x
                                      (->> x
                                           (.toString)
                                           (read-string)
                                           (apply merge))))]
                (when-not deathstar-edn
                  (host.chan/op
                   {::op.spec/op-key ::host.chan/show-info-msg}
                   channels
                   "workspace contains no deathstar.edn"))

                (when deathstar-edn
                  (do (set! *workspaceFolder* workspaceFolder))
                  (println ::extension-activate)
                  (println deathstar-edn)
                  (swap! state merge deathstar-edn)
                  (let []
                    (host.chan/op
                     {::op.spec/op-key ::host.chan/show-info-msg}
                     channels
                     "Death Star activating")
                    (socket.chan/op
                     {::op.spec/op-key ::socket.chan/connect}
                     channels
                     {::socket.spec/url (state->socket-url @state)})
                    (host.chan/op
                     {::op.spec/op-key ::host.chan/cmd}
                     (::host.chan/cmd| channels)
                     "deathstar.open")))))

            socket-evt|t
            (condp = (select-keys v [::op.spec/op-key ::op.spec/op-type])

              {::op.spec/op-key ::socket.chan/connected}
              (let []
                (println ::socket-connected)
                (hub.chan/op
                 {::op.spec/op-key ::hub.chan/user-join
                  ::op.spec/op-type ::op.spec/request}
                 channels
                 {::user.spec/uuid (cljc/rand-uuid)})
                (hub.chan/op
                 {::op.spec/op-key ::hub.chan/list-users
                  ::op.spec/op-type ::op.spec/request}
                 channels))

              {::op.spec/op-key ::socket.chan/closed}
              (let []
                (println ::socket-closed)))

            ops|
            (condp = (select-keys v [::op.spec/op-key ::op.spec/op-type])

              {::op.spec/op-key ::extension.chan/update-settings-filepaths
               ::op.spec/op-type ::op.spec/request}
              (let [{:keys [::extension.spec/deathstar-dir ::op.spec/out|]} @state
                    {:as resp :keys [::host.spec/filenames]} (<! (host.chan/op
                                                                  {::op.spec/op-key ::host.chan/read-dir
                                                                   ::op.spec/op-type ::op.spec/request}
                                                                  channels deathstar-dir))]
                (swap! state assoc ::extension.spec/settings-filepaths filenames)
                (extension.chan/op
                 {::op.spec/op-key ::extension.chan/update-settings-filepaths
                  ::op.spec/op-type ::op.spec/response}
                 out| filenames))

              {::op.spec/op-key ::extension.chan/apply-settings-file
               ::op.spec/op-type ::op.spec/request}
              (let [{:keys [::extension.spec/filepath ::op.spec/out|]} v
                    {:as resp :keys [::host.spec/file-content]} (<! (host.chan/op
                                                                     {::op.spec/op-key ::extension.chan/read-file
                                                                      ::op.spec/op-type ::op.spec/request}
                                                                     channels filepath))
                    settings (read-string file-content)]
                #_(swap! state merge (apply merge settings))
                #_(<! (ws.api/disconnect net-ws))
                #_(<! (remote.api/disconnect remote))
                #_(<! (ws.api/connect net-ws))
                #_(<! (remote.api/connect remote))
                (extension.chan/op
                 {::op.spec/op-key ::extension.chan/apply-settings-file
                  ::op.spec/op-type ::op.spec/response}
                 out| settings)))

            tab-evt|t
            {::op.spec/op-key ::host.chan/tab-disposed}
            (let []
              (println ::tab-disposed)
              (swap! state dissoc ::gui-tab))

            cmd|t
            (condp = (::host.spec/cmd-id v)

              (extension.spec/assert-cmd-id "deathstar.open")
              (cond

                (get @state ::gui-tab)
                (host.chan/op
                 {::op.spec/op-key ::host.chan/show-info-msg}
                 channels
                 "deathstar is already open")

                (not (get @state ::gui-tab))
                (let [tab-create-opts {::host.spec/tab-id "gui-tab"
                                       ::host.spec/tab-title "Death Star"
                                       ::host.spec/tab-html-replacements
                                       {"./out/deathstar-extension-gui/main.js" "resources/out/deathstar-extension-gui/main.js"
                                        "./css/style.css" "resources/antd.min-4.6.1.css"}
                                       ::host.spec/tab-html-filepath "resources/extension-gui.html"}]
                  (host.chan/op
                   {::op.spec/op-key ::host.chan/tab-create}
                   channels
                   tab-create-opts)
                  (host.chan/op
                   {::op.spec/op-key ::host.chan/show-info-msg}
                   channels
                   "deathstar opening")
                  (swap! state assoc ::gui-tab tab-create-opts)))

              (extension.spec/assert-cmd-id "deathstar.ping")
              (let [tab (get @state ::gui-tab)]
                (extension.gui.chan/op
                 {::op.spec/op-key ::extension.gui.chan/update-state}
                 channels
                 @state
                 (select-keys tab [::host.spec/tab-id]))
                #_(host.chan/op
                   {::op.spec/op-key ::host.chan/tab-send}
                   channels
                   {::some-value-value-to-send nil}))
              (host.chan/op
               {::op.spec/op-key ::host.chan/show-info-msg}
               channels
               "deathstar.ping"))))
        (recur))
      (println "; proc-ops go-block exiting"))))


(def ops (create-proc-ops channels state))


#_(defn create-proc-log
    [channels ctx]
    (let []
      (go (loop []
            (<! (chan 1))
            (recur))
          (println "; proc-log go-block exiting"))))


