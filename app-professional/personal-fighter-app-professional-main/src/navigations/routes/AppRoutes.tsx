import React from "react";
import {createStackNavigator} from "@react-navigation/stack";
import TabNavigator from "../../navigations/TabNavigator";

const AppStack = createStackNavigator();

const AppRoutes: React.FC = () => (
  <AppStack.Navigator screenOptions={{headerShown: false}}>
    <AppStack.Screen name={"Root"} component={TabNavigator}/>
  </AppStack.Navigator>
);

export default AppRoutes;