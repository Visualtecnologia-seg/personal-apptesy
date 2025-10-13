import {Dimensions, PixelRatio, Platform} from "react-native";

export const WHITE1 = "#efeeee";
export const WHITE2 = "#c7c7c7";
export const WHITE3 = "#616165";
export const LIGHTGREY = "#333335";
export const GREY = "#222224";
export const DARKGREY = "#1a1a1c";
export const YELLOW = "#dc9b3f";
export const RED = "#96031A";
export const GREEN = "#035a00";

export const OS = Platform.OS === "ios" ? "ios" : "android";
export const WIDTH = PixelRatio.getPixelSizeForLayoutSize(Dimensions.get("window").width);
export const SCREEN_WIDTH = Dimensions.get("screen").width;
export const HEIGHT = PixelRatio.getPixelSizeForLayoutSize(Dimensions.get("window").height);
export const SCREEN_HEIGHT = Dimensions.get("screen").height;

export const Theme = {
  colors: {
    primaryColor: YELLOW,
    primaryColorDark: DARKGREY,
    tintColor: "#73CEC1",
    black: "#404041",
    grayForBoxBackground: "#F3F4F8",
    lightgrey: "#eaeaea",
    grey: GREY,
    grayForItemsMore: "#b0b0b0",
    grayForItemsArrow: "#c2c2c2",
    touchableHighlightUnderlayColor: "#f0f0f0",
    dividerColor: "#f0f0f0",
    scrollviewBackground: "#F3F4F5",
    calendarItem: {
      backgroundColor: "#18b4cb10",
      leftColor: YELLOW,
      timeColor: DARKGREY
    }
  },
  nextButton: {
    colors: {
      primary: YELLOW,
      secondary: RED,
      disabled: LIGHTGREY,
      selected: DARKGREY,
      unselected: LIGHTGREY,
      border: LIGHTGREY,
      confirmed: YELLOW,
      canceled: RED,
      done: YELLOW,
      open: WHITE2
    }
  },
  textColor: {
    primary: WHITE1,
    secondary: WHITE2,
    white: "white",
    yellow: YELLOW,
    red: RED,
    dark: DARKGREY,
    placeholder: "#4e4e4e"
  },
  textSize: {
    section: OS === "android" ? 20 : 21,
    headerTitle: OS === "android" ? 18 : 17,
    primary: OS === "android" ? 16 : 17,
    secondary: OS === "android" ? 13 : 15
  },
  tabBar: {
    backgroundColor: DARKGREY,
    iconColor: YELLOW,
    primaryTextColor: YELLOW,
    secondaryTextColor: WHITE3
  },
  header: {
    backgroundColor: DARKGREY,
    textColor: YELLOW
  },
  card: {
    backgroundColor: LIGHTGREY
  },
  iconColor: YELLOW,
  scrollViewBackgroundColor: GREY,
  shadowColor: DARKGREY,
  imageBorderColor: DARKGREY
};