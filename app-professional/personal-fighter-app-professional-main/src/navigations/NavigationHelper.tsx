import React from "react";
import {ParamListBase, RouteProp} from "@react-navigation/native";
import {CardStyleInterpolators, StackNavigationOptions} from "@react-navigation/stack";
import {BottomTabNavigationOptions} from "@react-navigation/bottom-tabs";
import {Feather} from "@expo/vector-icons";
import {NavigationNames} from "./NavigationNames";
import {Theme} from "../theme";

const getTabTitle = (routeName: string): string => {
  if (routeName === NavigationNames.HomeTab) {
    return "Pedidos";
  } else if (routeName === NavigationNames.OrderTab) {
    return "Minhas Aulas";
  } else if (routeName === NavigationNames.ScheduleTab) {
    return "Minha Agenda";
  } else if (routeName === NavigationNames.ProfileTab) {
    return "Perfil";
  } else {
    return "";
  }
};

export const tabScreenOptions: (props: {
  route: RouteProp<ParamListBase, keyof ParamListBase>;
  navigation: any;
}) => BottomTabNavigationOptions = ({route}) => ({
  title: getTabTitle(route.name),
  tabBarIcon: ({focused, color, size}) => {
    let icon = "";
    switch (route.name) {
      case NavigationNames.HomeTab:
        icon = "list";
        break;
      case NavigationNames.OrderTab:
        icon = "calendar";
        break;
      case NavigationNames.ScheduleTab:
        icon = "check-square";
        break;
      case NavigationNames.ProfileTab:
        icon = "user";
        break;
    }
    // @ts-ignore
    return <Feather size={22} color={color} name={icon}/>;
  },
});

export const stackScreenOptions: StackNavigationOptions = {
  headerTitleStyle: {color: Theme.header.textColor},
  headerTintColor: Theme.header.textColor,
  headerTitleAlign: "center",
  headerBackTitleVisible: false,
  cardStyle: {
    backgroundColor: Theme.header.backgroundColor,
  },
  headerStyle: {
    backgroundColor: Theme.header.backgroundColor,
    borderBottomColor: Theme.header.backgroundColor,
  },
  gestureEnabled: false,
  cardStyleInterpolator: CardStyleInterpolators.forHorizontalIOS,
};
