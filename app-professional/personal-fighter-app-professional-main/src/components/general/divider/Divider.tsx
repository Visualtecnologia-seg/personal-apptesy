import React from "react";
import {StyleSheet, View, ViewStyle} from "react-native";
import {SCREEN_WIDTH, Theme} from "../../../theme";

type TProps = {
  style?: ViewStyle;
  h16?: boolean;
};

export const Divider: React.FC<TProps> = props => {
  return (
    <View
      style={[
        styles.container,
        props.style,
      ]}
    />
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: Theme.tabBar.secondaryTextColor,
    height: 0.5,
    marginHorizontal: SCREEN_WIDTH / 8,
    marginVertical: 5
  }
});
