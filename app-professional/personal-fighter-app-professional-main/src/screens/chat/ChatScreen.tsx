import React, {useCallback, useEffect, useState} from "react";
import {Bubble, GiftedChat, InputToolbar, Send} from "react-native-gifted-chat";
import {useAuth} from "../../context/AuthContext";
import {Theme, YELLOW} from "../../theme";
import {MaterialIcons} from "@expo/vector-icons";
import "dayjs/locale/pt-br";
import {firebase, pushMessage} from "../../services/FirebaseService";
import {ChatMessageModel} from "../../model/ChatMessageModel";
import dayjs from "dayjs";
import {getCustomer} from "../../services/CustomerService";
import {Loading} from "../../components/general/loading";
import {UserModel} from "../../model";
import {sendNotification} from "../../services/NotificationService";
import {NotificationModel} from "../../model/NotificationModel";

// @ts-ignore
export const ChatScreen: React.FC = ({route}) => {
  /* Default */
  const {order} = route?.params;
  const [loading, setLoading] = useState<boolean>(true);
  const {user} = useAuth();
  /* Chat */
  const [customer, setCustomer] = useState<UserModel>({});
  const [messages, setMessages] = useState([]);

  const sortMessages = (messages: ChatMessageModel[]) => {
    messages.sort(((a, b) => {
      if (a.createdAt > b.createdAt) {
        return -1;
      }
      if (a.createdAt < b.createdAt) {
        return 1;
      }
      return 0;
    }));
    return messages;
  };

  useEffect(() => {
    getCustomer(order?.customer?.id).then(res => {
      setCustomer(res.data);
    });
    setLoading(false);
  }, []);

  useEffect(() => {
    /* buscam as mensagens e cria um listener para todas as alterações nas mensagens */
    firebase.database().ref("chats").child(order.id).child("messages")
      .on("value", (snapshot) => {
        let arrayMessages = [];
        snapshot?.forEach(child => {
          arrayMessages.push({
            _id: child.key,
            createdAt: child.val().createdAt,
            text: child.val().text,
            user: child.val().user
          });
        });
        setMessages(sortMessages(arrayMessages));
      });
    /* Remove o listener quando sair da tela */
    return () => firebase.database().ref("chats").child(order.id).child("messages").off();
  }, [customer]);

  const onSend = useCallback((newMessages = []) => {
    let arrayMessages = [...messages];
    newMessages.forEach(value => {
      value = {...value, createdAt: dayjs(new Date()).toISOString()};
      arrayMessages.push(value);
    });
    pushMessage(order.id, arrayMessages);

    let data: NotificationModel = {
      from: {id: user.id, name: user.name},
      to: {id: customer.id, name: customer.name},
      body: newMessages[0].text,
      data: null,
      fromRole: "PROFESSIONAL",
      toRole: "CUSTOMER"
    };
    sendNotification(data).then(() => {
    });
  }, [messages]);

  if (loading) {
    return (<Loading/>);
  }

  function renderBubble(props) {
    return (
      <Bubble
        {...props}
        wrapperStyle={{
          left: {
            backgroundColor: Theme.card.backgroundColor
          },
          right: {
            backgroundColor: Theme.card.backgroundColor
          }
        }}
        textStyle={{
          left: {color: Theme.textColor.primary, padding: 10},
          right: {color: Theme.textColor.primary, padding: 10}
        }}
      />
    );
  }

  function renderInput(props) {
    return (
      <InputToolbar
        {...props}
        containerStyle={{backgroundColor: Theme.card.backgroundColor}}
      />
    );
  }

  const renderSend = (props: Send["props"]) => (
    <Send {...props} containerStyle={{justifyContent: "center", marginRight: 10}}>
      <MaterialIcons size={30} color={YELLOW} name={"send"}/>
    </Send>
  );

  return (
    <GiftedChat
      messages={messages}
      onSend={messages => onSend(messages)}
      user={{
        _id: user?.cpf,
        name: user?.name,
        avatar: user?.avatarUrl
      }}
      messagesContainerStyle={{backgroundColor: Theme.scrollViewBackgroundColor, paddingHorizontal: 16}}
      locale={"pt-br"}
      timeTextStyle={{
        left: {color: Theme.textColor.primary, paddingLeft: 10, paddingBottom: 10},
        right: {color: Theme.textColor.primary, paddingRight: 10, paddingBottom: 10}
      }}
      textInputProps={{color: Theme.textColor.primary}}
      renderBubble={renderBubble}
      renderSend={renderSend}
      renderInputToolbar={renderInput}
      placeholder={"Escreva uma mensagem..."}
    />
  );
};
