import React from "react";
import {CardStyleInterpolators, createStackNavigator} from "@react-navigation/stack";

import {NavigationNames} from "../NavigationNames";
import {RegisterTwoScreen} from "../../screens/auth/register/RegisterTwoScreen";
import {AuthScreen} from "../../screens/auth/AuthScreen";
import {RegisterOneScreen} from "../../screens/auth/register/RegisterOneScreen";
const AuthStack = createStackNavigator();

const AuthRoutes: React.FC = () => (
  <AuthStack.Navigator initialRouteName="Auth" screenOptions={{
    headerShown: false,
    cardStyleInterpolator: CardStyleInterpolators.forHorizontalIOS,
  }}>
    <AuthStack.Screen name="Auth" component={AuthScreen}/>
    <AuthStack.Screen name="Register" component={RegisterOneScreen}/>
    <AuthStack.Screen name={NavigationNames.RegisterScreenTwo} component={RegisterTwoScreen}/>
  </AuthStack.Navigator>
);

export default AuthRoutes;