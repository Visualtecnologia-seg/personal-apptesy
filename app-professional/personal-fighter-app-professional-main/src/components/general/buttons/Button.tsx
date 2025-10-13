import React from "react";
import {StyleSheet, Text, TextStyle, TouchableOpacity, ViewStyle} from "react-native";
import {Theme} from "../../../theme";

type TProps = {
  title: string;
  style?: ViewStyle;
  type?: "default" | "outline";
  size?: "default" | "small";
  onPress?: () => void;
  textColor?: TextStyle;
  disabled?: boolean
};

export const Button: React.FC<TProps> = props => {
  return (
    <TouchableOpacity
      style={[
        styles.container,
        props.style,
        props.type === "outline" && styles.outlineContainer,
      ]}
      onPress={props.onPress}
      disabled={props?.disabled}
    >
      <Text
        style={[
          styles.text,
          props.type === "outline" && styles.outlineText,
          props.size === "small" && {
            fontSize: Theme.textSize.primary,
          },
          props.textColor,
        ]}
      >
        {props.title}
      </Text>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: 22,
    height: 38,
    backgroundColor: Theme.nextButton.colors.primary,
    borderRadius: 4,
    alignItems: "center",
    justifyContent: "center",
  },
  outlineContainer: {
    backgroundColor: "transparent",
    borderWidth: 0.5,
    borderColor: Theme.colors.grey,
  },
  text: {fontSize: Theme.textSize.primary, color: Theme.textColor.secondary},
  outlineText: {
    color: Theme.colors.grey,
    fontWeight: "400",
  },
});
