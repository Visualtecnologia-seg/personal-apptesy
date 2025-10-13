import React, {useEffect, useRef, useState} from "react";

import {useAuth} from "../../context/AuthContext";
import AppRoutes from "./AppRoutes";
import * as Device from "expo-device";
import * as Notifications from "expo-notifications";
import {Platform} from "react-native";
import AuthRoutes from "./AuthRoutes";
import {usePushNotification} from "../../context/PushNotificationContext";
import NetInfo from "@react-native-community/netinfo";
import OfflineComponent from "../../components/OfflineComponent";
import {useUserRecords} from "../../context/UserRecordsContext";
import {UserRecordsModel} from "../../model/UserRecordsModel";
import * as SecureStore from "expo-secure-store";
import {saveRecords} from "../../services/UserService";
import Constants from "expo-constants";

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: false,
    shouldSetBadge: false
  })
});

const Routes: React.FC = () => {
  const {isSignedIn} = useAuth();
  const {user} = useAuth();
  const {pushNotification, setPushNotification} = usePushNotification();
  const {setUserRecords} = useUserRecords();
  const [notification, setNotification] = useState(false);
  const notificationListener = useRef();
  const responseListener = useRef();
  const [isOnline, setIsOnline] = useState(true);

  useEffect(() => {
    const unsubscribe = NetInfo.addEventListener(state => {
      setIsOnline(state.isConnected);
    });

    registerForPushNotificationsAsync().then(async expoToken => {
      let records = {} as UserRecordsModel;
      records.deviceBrand = Device.brand;
      records.deviceModel = Device.modelName;
      records.deviceModelId = Device.modelId;
      records.deviceOsVersion = Device.osVersion;
      records.professionalExpoPushNotificationToken = expoToken;
      records.user = user;
      await SecureStore.setItemAsync("personal-user-records", JSON.stringify(records)).then();
      saveRecords(records).then();
      setUserRecords(records);
    });

    // @ts-ignore
    notificationListener.current = Notifications.addNotificationReceivedListener(notification => {
      // @ts-ignore
      setPushNotification(notification);
      // @ts-ignore
      setNotification(notification);
    });

    // @ts-ignore
    responseListener.current = Notifications.addNotificationResponseReceivedListener(response => {
      console.log(response);
    });

    return () => {
      // @ts-ignore
      Notifications.removeNotificationSubscription(notificationListener);
      // @ts-ignore
      Notifications.removeNotificationSubscription(responseListener);
      unsubscribe();
    };
  }, [user]);

  async function registerForPushNotificationsAsync() {
    let token;
    if (Constants.isDevice) {
      const {status: existingStatus} = await Notifications.getPermissionsAsync();
      let finalStatus = existingStatus;
      if (existingStatus !== "granted") {
        const {status} = await Notifications.requestPermissionsAsync();
        finalStatus = status;
      }
      if (finalStatus !== "granted") {
        alert("Failed to get push token for push notification!");
        return;
      }
      token = (await Notifications.getExpoPushTokenAsync()).data;
    } else {
      // alert("Must use physical device for Push Notifications");
    }

    if (Platform.OS === "android") {
      await Notifications.setNotificationChannelAsync("default", {
        name: "default",
        importance: Notifications.AndroidImportance.MAX,
        vibrationPattern: [0, 250, 250, 250],
        lightColor: "#FF231F7C"
      });
    }
    return token;
  }

  async function schedulePushNotification() {
    await Notifications.scheduleNotificationAsync({
      content: {
        title: "You've got mail! 📬",
        body: "Here is the notification body",
        data: {data: "goes here"}
      },
      trigger: {seconds: 2}
    });
  }

  return (
    <>
      {!isOnline && <OfflineComponent/>}
      {isOnline && isSignedIn && <AppRoutes/>}
      {isOnline && !isSignedIn && <AuthRoutes/>}
    </>
  );
};

export default Routes;
