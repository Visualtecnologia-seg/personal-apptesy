import React, {useEffect} from "react";
import {NavigationContainer} from "@react-navigation/native";
import * as eva from "@eva-design/eva";
import {ApplicationProvider} from "@ui-kitten/components";
import {StatusBar} from "react-native";
import {SafeAreaView} from "react-native-safe-area-context";
import {default as theme} from "../theme/ui-kitten-constants.json";
import {default as mapping} from "../theme/ui-kitten-mapping.json";
import {DARKGREY, OS} from "../theme";
import AppProvider from "../context";
import Routes from "./routes/Routes";
import "moment";
import "moment/locale/pt";
import {setCustomText} from "react-native-global-props";
import {useFonts} from "expo-font";
import MenuItem from "react-native-paper/lib/typescript/components/Menu/MenuItem";

const Theme = {
  dark: true,
  colors: {
    primary: "rgb(255, 45, 85)",
    background: DARKGREY,
    card: "rgb(255, 255, 255)",
    text: "rgb(28, 28, 30)",
    border: "rgb(199, 199, 204)",
    notification: "rgb(255, 69, 58)",
  },
};

export default function () {
  const [loaded] = useFonts({
    Montserrat: require("../../assets/fonts/Montserrat-Regular.ttf"),
    OpenSans: require("../../assets/fonts/OpenSans-Regular.ttf"),
    Fighter: require("../../assets/fonts/Fighter.ttf"),
    Dogfight: require("../../assets/fonts/Dogfight-Italic.ttf"),
  });

  useEffect(() => {
    if (loaded) {
      const customTextProps = {
        style: {
          fontFamily: "OpenSans",
        },
      };
      setCustomText(customTextProps);
    }
  }, [loaded]);

  return (
    <NavigationContainer theme={Theme}>
      <ApplicationProvider {...eva} theme={{...eva.dark, ...theme}} customMapping={mapping}>
        <AppProvider>
          <SafeAreaView style={{flex: 1, backgroundColor: DARKGREY}}>
            <StatusBar backgroundColor={OS === "android" ? DARKGREY : DARKGREY}/>
            {loaded && <Routes/>}
          </SafeAreaView>
        </AppProvider>
      </ApplicationProvider>
    </NavigationContainer>
  );
}
