(ns status-im.ui.screens.desktop.main.chat.views
  (:require-macros [status-im.utils.views :as views])
  (:require [re-frame.core :as re-frame]
            [status-im.ui.components.icons.vector-icons :as icons]
            [clojure.string :as string]
            [status-im.ui.screens.chat.styles.message.message :as message.style]
            [status-im.ui.screens.chat.message.message :as message]
            [taoensso.timbre :as log]
            [reagent.core :as reagent]
            [status-im.ui.screens.chat.utils :as chat-utils]
            [status-im.utils.gfycat.core :as gfycat]
            [status-im.constants :as constants]
            [status-im.utils.identicon :as identicon]
            [status-im.utils.datetime :as time]
            [status-im.ui.components.button.view :as button]
            [status-im.utils.utils :as utils]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.connectivity.view :as connectivity]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.screens.chat.message.datemark :as message.datemark]
            [status-im.ui.screens.desktop.main.tabs.profile.views :as profile.views]
            [status-im.ui.components.icons.vector-icons :as vector-icons]
            [status-im.ui.screens.desktop.main.chat.styles :as styles]
            [status-im.contact.db :as contact.db]
            [status-im.ui.components.popup-menu.views :refer [show-desktop-menu
                                                              get-chat-menu-items]]
            [status-im.i18n :as i18n]
            [status-im.ui.screens.desktop.main.chat.events :as chat.events]
            [status-im.ui.screens.chat.message.message :as chat.message]))

(views/defview toolbar-chat-view [{:keys [chat-id color public-key public? group-chat]
                                   :as current-chat}]
  (views/letsubs [chat-name         [:chats/current-chat-name]
                  {:keys [pending? public-key photo-path]} [:chats/current-chat-contact]]
    [react/view {:style styles/toolbar-chat-view}
     [react/view {:style {:flex-direction :row
                          :flex 1}}
      (if public?
        [react/view {:style (styles/topic-image color)}
         [react/text {:style styles/topic-text}
          (string/capitalize (second chat-name))]]
        [react/image {:style styles/chat-icon
                      :source {:uri photo-path}}])
      [react/view {:style (styles/chat-title-and-type pending?)}
       [react/text {:style styles/chat-title
                    :font  :medium}
        chat-name]
       (cond pending?
             [react/text {:style styles/add-contact-text
                          :on-press #(re-frame/dispatch [:contact.ui/add-to-contact-pressed public-key])}
              (i18n/label :t/add-to-contacts)]
             public?
             [react/text {:style styles/public-chat-text}
              (i18n/label :t/public-chat)])]]
     [react/touchable-highlight
      {:on-press #(show-desktop-menu
                   (get-chat-menu-items group-chat public? chat-id))}
      [vector-icons/icon :icons/dots-horizontal
       {:style {:tint-color colors/black
                :width      24
                :height     24}}]]]))

(views/defview message-author-name [{:keys [from]}]
  (views/letsubs [incoming-name   [:contacts/contact-name-by-identity from]]
    [react/view {:flex-direction :row}
     (when incoming-name
       [react/text {:style styles/author} incoming-name])
     [react/text {:style styles/author-generated}
      (str (when incoming-name " • ") (gfycat/generate-gfy from))]]))

(views/defview member-photo [from]
  (views/letsubs [current-public-key [:account/public-key]
                  photo-path [:chats/photo-path from]]
    [react/view {:style {:width 40 :margin-horizontal 16}}
     [react/view {:style {:position :absolute}}
      [react/touchable-highlight {:on-press #(when-not (= current-public-key from)
                                               (re-frame/dispatch [:show-profile-desktop from]))}
       [react/view {:style styles/member-photo-container}
        [react/image {:source {:uri (if (string/blank? photo-path)
                                      (identicon/identicon from)
                                      photo-path)}
                      :style  styles/photo-style}]]]]]))

(views/defview quoted-message [{:keys [from text]} outgoing current-public-key]
  (views/letsubs [username [:contacts/contact-name-by-identity from]]
    [react/view {:style styles/quoted-message-container}
     [react/view {:style styles/quoted-message-author-container}
      [icons/icon :icons/reply {:style           (styles/reply-icon outgoing)
                                :width           16
                                :height          16
                                :container-style (when outgoing {:opacity 0.4})}]
      [react/text {:style (message.style/quoted-message-author outgoing)}
       (chat-utils/format-reply-author from username current-public-key)]]
     [react/text {:style           (message.style/quoted-message-text outgoing)
                  :number-of-lines 5}
      text]]))

(defn- message-sent? [user-statuses current-public-key]
  (not= (get-in user-statuses [current-public-key :status]) :not-sent))

(views/defview message-without-timestamp
  [text {:keys [message-id old-message-id content current-public-key user-statuses] :as message} style]
  [react/view {:flex 1 :margin-vertical 5}
   [react/touchable-highlight {:on-press (fn [arg]
                                           (when (= "right" (.-button (.-nativeEvent arg)))
                                             (show-desktop-menu
                                              [{:text (i18n/label :t/sharing-copy-to-clipboard)
                                                :on-select #(do (utils/show-popup "" "Message copied to clipboard") (react/copy-to-clipboard text))}
                                               {:text (i18n/label :t/message-reply)
                                                :on-select #(when (message-sent? user-statuses current-public-key)
                                                              (re-frame/dispatch [:chat.ui/reply-to-message message-id old-message-id]))}])))}
    [react/view {:style styles/message-container}
     [react/text {:style           (styles/message-text false)
                  :selectable      true
                  :selection-color colors/blue-light}
      (if-let [render-recipe (:render-recipe content)]
        (chat-utils/render-chunks render-recipe message)
        (:text content))]]]])

(views/defview photo-placeholder []
  [react/view {:style {:width             40
                       :margin-horizontal 16}}])

(views/defview system-message [text {:keys [content from timestamp] :as message}]
  [react/view
   [react/view {:style {:flex-direction :row :margin-top 24}}
    [member-photo from]
    [react/view {:style {:flex 1}}]
    [react/text {:style styles/message-timestamp}
     (time/timestamp->time timestamp)]]
   [react/view {:style styles/not-first-in-group-wrapper}
    [photo-placeholder]
    [react/text {:style styles/system-message-text}
     text]]])

(views/defview message-with-name-and-avatar [text {:keys [from timestamp] :as message}]
  [react/view
   [react/view {:style {:flex-direction :row :margin-top 24}}
    [member-photo from]
    [message-author-name message]
    [react/view {:style {:flex 1}}]
    [react/text {:style styles/message-timestamp}
     (time/timestamp->time timestamp)]]
   [react/view {:style styles/not-first-in-group-wrapper}
    [photo-placeholder]
    [message-without-timestamp text message]]])

(defmulti message (fn [_ _ {:keys [content-type]}] content-type))

(defmethod message constants/content-type-command
  [_ _ {:keys [from] :as message}]
  [react/view
   [react/view {:style {:flex-direction :row :align-items :center :margin-top 15}}
    [member-photo from]
    [message-author-name message]]
   [react/view {:style styles/not-first-in-group-wrapper}
    [photo-placeholder]
    [react/view {:style styles/message-command-container}
     [message/message-content-command message]]]])

(views/defview message-content-status [text message]
  [react/view
   [system-message text message]])

(defmethod message constants/content-type-status
  [text _ message]
  [message-content-status text message])

(defmethod message :default
  [text me? {:keys [message-id chat-id message-status user-statuses from seen
                    current-public-key content-type outgoing type value] :as message}]
  (when (contains? constants/desktop-content-types content-type)
    (when (nil? message-id)
      (log/debug "nil?" message))
    ^{:key (str "message" message-id)}
    [react/view {:style {:background-color (if seen
                                             colors/white
                                             colors/blue-light)}}
     [message-with-name-and-avatar text message]
     #_[react/view {:style (message.style/delivery-status outgoing)}
        [message/message-delivery-status message]]]))

(defn inc-last [current-path]
  (conj (pop current-path) (inc (peek current-path))))

(defn dec-last [current-path]
  (conj (pop current-path) (dec (peek current-path))))

(defn childs [{:keys [children]}]
  (apply +
         (count children)
         (map #(childs %) children)))

(views/defview send-button [inp-ref]
  (views/letsubs [{:keys [input-text]} [:chats/current-chat]]
    (let [empty? (= "" input-text)]
      [react/touchable-highlight {:style    styles/send-button
                                  :on-press (fn []
                                              (.clear @inp-ref)
                                              (.focus @inp-ref)
                                              (re-frame/dispatch [:chat.ui/send-current-message]))}
       [react/view {:style (styles/send-icon false)}
        [icons/icon :icons/arrow-left {:style (styles/send-icon-arrow false)}]]])))

(defn chat-text-input [chat-id response-id]
  (let [input-ref (atom nil)
        input-text (atom "")]
    [react/view {:style styles/chat-box}
     [react/text-input {:placeholder            (i18n/label :t/type-a-message)
                        :auto-focus             true
                        :multiline              true
                        :blur-on-submit         true
                        :style                  styles/chat-text-input
                        :ref #(reset! input-ref %)
                        :font                   :default
                        :submit-shortcut        {:key "Enter"}
                        :on-submit-editing      #(do
                                                   (.clear @input-ref)
                                                   (.focus @input-ref)
                                                   (println chat-id)
                                                   (re-frame/dispatch [:chat/send-plain-text-message @input-text chat-id response-id]))
                        :on-change              (fn [e]
                                                  (let [native-event (.-nativeEvent e)
                                                        text         (.-text native-event)]
                                                    (reset! input-text text)))}]
     [send-button input-ref]]))

(defn tag-view [tag {:keys [on-press]}]
  [react/touchable-highlight {:style {:border-radius 5
                                      :margin 2
                                      :padding 4
                                      :height 23
                                      :background-color (if (= tag "clear filter")
                                                          colors/red
                                                          colors/blue)
                                      :justify-content :center
                                      :align-items :center
                                      :align-content :center}
                              :on-press on-press}
   [react/text {:style {:font-size 9
                        :color colors/white}
                :font :medium} tag]])

(defn messages-view
  [{:keys [children-fn parent-fn next-sibling-fn previous-sibling-fn next-siblings previous-siblings parent children message-id content] :as message-obj} reply?]
  [react/view {:flex 1
               :flex-direction :row}
   (when previous-sibling-fn
     [button/primary-button {:style {:flex 1}
                             :on-press previous-sibling-fn}
      (str "Previous " (count previous-siblings))])
   [react/view {:flex 1
                :flex-direction :column}
    (when parent-fn
      [button/primary-button {:flex 1
                              :on-press parent-fn} "Parent"])
    [tag-view  (:chat-id content) {}]
    [react/view {:style {:flex 1}}
     ^{:key message-id}

     [message (:text content) false message-obj]]
    [chat-text-input (:chat-id content) message-id]
    (when children-fn
      [button/primary-button {:flex 1
                              :on-press children-fn}
       (str "see " (count children) " replies")])]
   (when next-sibling-fn
     [button/primary-button {:style {:flex 1}
                             :on-press next-sibling-fn}
      (str "Next " (count next-siblings))])])

(views/defview chat-view []
  (views/letsubs [message [:messages/current-message]]
    [react/view {:style styles/chat-view}
     [react/view {:style styles/separator}]
     [react/view {:flex 1
                  :flex-direction :column}
      [messages-view message (reagent/atom false)]]]))
