(ns status-im.messages.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::messages
 (fn [db]
   (:messages db)))

(re-frame/reg-sub
 ::current-message-id
 (fn [db]
   (get db :current-message-id)))

(re-frame/reg-sub
 :messages/roots
 :<- [::messages]
 (fn [messages]
   (keys (remove #((comp :parent val) %) messages))))

(re-frame/reg-sub
 :messages/current-message-id
 :<- [::current-message-id]
 :<- [:messages/roots]
 (fn [[current-message-id roots]]
   (or current-message-id
       (first roots))))

(defn navigate-to [message-id]
  #(re-frame/dispatch [:messages/set-current-message-id message-id]))

(re-frame/reg-sub
 :messages/current-message
 :<- [:messages/roots]
 :<- [:messages/current-message-id]
 :<- [::messages]
 (fn [[roots current-message-id messages]]
   (let [{:keys [parent children] :as message} (get messages current-message-id)
         siblings  (or (:children (get messages parent))
                       roots)
         [previous-siblings [_ & next-siblings]] (split-with #(not= % current-message-id) (into #{} siblings))]
     (cond-> message
       parent
       (assoc :parent-fn (navigate-to parent))

       (not-empty children)
       (assoc :children-fn (navigate-to (first children)))

       (not-empty previous-siblings)
       (assoc :previous-siblings previous-siblings
              :previous-sibling-fn (navigate-to (last previous-siblings)))

       (not-empty next-siblings)
       (assoc :next-siblings next-siblings
              :next-sibling-fn (navigate-to (first next-siblings)))))))
