import React from "react";
import {createStackNavigator} from "@react-navigation/stack";
import {createBottomTabNavigator} from "@react-navigation/bottom-tabs";
import {Theme} from "../theme";
import {NavigationNames} from "./NavigationNames";
import {stackScreenOptions, tabScreenOptions} from "./NavigationHelper";
import {ChatScreen} from "../screens/chat/ChatScreen";
import {DataScreen} from "../screens/profile/DataScreen";

import {HomeScreen} from "../screens/home/HomeScreen";
import {OrderScreen} from "../screens/order/OrderScreen";
import {ScheduleScreen} from "../screens/schedule/ScheduleScreen";
import {ProfileScreen} from "../screens/profile/ProfileScreen";
import {ResumeScreen} from "../screens/profile/ResumeScreen";
import {PhotoAlbumScreen} from "../screens/profile/PhotoAlbumScreen";
import {ProductScreen} from "../screens/profile/ProductScreen";
import {TermsOfService} from "../screens/profile/TermsOfService";
import {PrivacyPolicy} from "../screens/profile/PrivacyPolicy";
import {ContactUsScreen} from "../screens/profile/ContactUsScreen";
import {ChangePasswordScreen} from "../screens/profile/ChangePasswordScreen";
import {getFocusedRouteNameFromRoute} from "@react-navigation/native";
import {FinanceScreen} from "../screens/profile/FinanceScreen";

const Stack = createStackNavigator();
const Tab = createBottomTabNavigator();

const tabHiddenRoutes = [
  NavigationNames.HomeScreen,
  NavigationNames.ProfileScreen,
  NavigationNames.OrderScreen,

];

const HomeTabStack = () => {
  return (
    <Stack.Navigator headerMode="screen" screenOptions={stackScreenOptions}>
      <Stack.Screen
        name={NavigationNames.HomeScreen}
        component={HomeScreen}
        options={{title: "Pedidos"}}
      />
      <Stack.Screen
        name={NavigationNames.ChatScreen}
        component={ChatScreen}
        options={{title: "Chat"}}
      />
    </Stack.Navigator>

  );
};

const OrderTabStack = () => {
  return (
    <Stack.Navigator headerMode="screen" screenOptions={stackScreenOptions}>
      <Stack.Screen
        name={NavigationNames.OrderScreen}
        component={OrderScreen}
        options={{title: "Minhas aulas"}}
      />
      <Stack.Screen
        name={NavigationNames.ChatScreen}
        component={ChatScreen}
        options={{title: "Chat"}}
      />
    </Stack.Navigator>

  );
};

const ScheduleTabStack = () => {
  return (
    <Stack.Navigator headerMode="screen" screenOptions={stackScreenOptions}>
      <Stack.Screen
        name={NavigationNames.ScheduleTab}
        component={ScheduleScreen}
        options={{title: "Minha Agenda"}}
      />
    </Stack.Navigator>
  );
};

const ProfileTabStack = ({navigation, route}) => {
  React.useLayoutEffect(() => {
    let routeName = getFocusedRouteNameFromRoute(route);
    if (tabHiddenRoutes.includes(routeName) || routeName === undefined) {
      navigation.setOptions({tabBarVisible: true});
    } else {
      navigation.setOptions({tabBarVisible: false});
    }
  }, [navigation, route]);
  return (
    <Stack.Navigator keyboardHandlingEnabled={false} headerMode="screen" screenOptions={stackScreenOptions} >
      <Stack.Screen
        name={NavigationNames.ProfileScreen}
        component={ProfileScreen}
        options={{title: "Meu Perfil"}}
      />
      <Stack.Screen
          name={NavigationNames.DataScreen}
          component={DataScreen}
          options={{title: "Meus dados"}}
      />
      <Stack.Screen
        name={NavigationNames.FinanceScreen}
        component={FinanceScreen}
        options={{title: "Chaves PIX"}}
      />
      <Stack.Screen
        name={NavigationNames.ProductScreen}
        component={ProductScreen}
        options={{title: "Minhas modalidades"}}
      />
      <Stack.Screen
        name={NavigationNames.ResumeScreen}
        component={ResumeScreen}
        options={{title: "Meu currículo"}}
      />
      <Stack.Screen
        name={NavigationNames.PhotoAlbumScreen}
        component={PhotoAlbumScreen}
        options={{title: "Minhas fotos"}}
      />
      <Stack.Screen
        name={NavigationNames.ChangePasswordScreen}
        component={ChangePasswordScreen}
        options={{title: "Alterar senha"}}
      />
      <Stack.Screen
        name={NavigationNames.ContactUsScreen}
        component={ContactUsScreen}
        options={{title: "Fale conosco"}}
      />
      <Stack.Screen
        name={NavigationNames.PrivacyPolicy}
        component={PrivacyPolicy}
        options={{title: "Política de privacidade"}}
      />
      <Stack.Screen
        name={NavigationNames.TermsOfService}
        component={TermsOfService}
        options={{title: "Termos de uso"}}
      />
    </Stack.Navigator>
  );
};

const TabNavigator = () => (
  <Tab.Navigator
    screenOptions={tabScreenOptions}
    tabBarOptions={{
      activeTintColor: Theme.tabBar.primaryTextColor,
      inactiveTintColor: Theme.tabBar.secondaryTextColor,
      tabStyle: {
        backgroundColor: Theme.tabBar.backgroundColor,
        borderTopColor: Theme.tabBar.backgroundColor,
        paddingVertical: 5,
      },
      style: {
        height: 56,
        borderTopWidth: 0
      },
      keyboardHidesTabBar: true
    }}

  >
    <Tab.Screen name={NavigationNames.HomeTab} component={HomeTabStack}/>
    <Tab.Screen name={NavigationNames.OrderTab} component={OrderTabStack}/>
    <Tab.Screen name={NavigationNames.ScheduleTab} component={ScheduleTabStack}/>
    <Tab.Screen name={NavigationNames.ProfileTab} component={ProfileTabStack}/>
  </Tab.Navigator>
);

export default TabNavigator;
