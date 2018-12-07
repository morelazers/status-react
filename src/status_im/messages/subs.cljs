(ns status-im.messages.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::messages
 (fn [db]
   (:messages db)))

(defn seen-reply?
  [{:keys [seen? parent]}]
  (and seen? parent))

(re-frame/reg-sub
 :messages/messages
 :<- [::messages]
 (fn [messages]
   (into {} (remove #((comp seen-reply? val) %) messages))))

(re-frame/reg-sub
 :messages/by-parents
 :<- [::messages]
 (fn [messages]
   (reduce (fn [acc [message-id {:keys [parent]}]]
             (update acc (or parent :root) conj message-id))
           {}
           messages)))

(defn add-children
  [{:keys [message-id] :as message} by-parents messages]
  (if-let [children-ids (get by-parents message-id)]
    (assoc message
           :children
           (mapv #(add-children (get messages %) by-parents messages) children-ids))
    message))

(re-frame/reg-sub
 :messages/roots3
 :<- [:messages/by-parents]
 :<- [::messages]
 (fn [[by-parents messages]]
   (mapv (fn [message-id]
           (add-children (get messages message-id) by-parents messages))
         (:root by-parents))))

#_(re-frame/reg-sub
   :messages/current-message
   :<- [:messages/by-parents]
   :<- [::messages]
   (fn [[by-parents messages]]
     (map (fn [message-id]
            (add-children (get messages message-id) by-parents messages))
          (:root by-parents))))
