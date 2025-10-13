import {StyleSheet} from "react-native";
import {Theme} from "../../theme";


export const themeStyles = StyleSheet.create({
  safeArea: {
    flex: 1,
    // marginTop: 10,
  },
  text: {
    flex: 1,
    backgroundColor: Theme.scrollViewBackgroundColor,
    paddingHorizontal: 16,
    paddingTop: 16,
  },
  title: {
    fontSize: 16,
  },
});
