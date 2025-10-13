import React from "react";
import {ActivityIndicator, Image, ImageStyle, StyleSheet, View, ViewStyle} from "react-native";
import {Ionicons} from "@expo/vector-icons";
import {YELLOW} from "../../../theme";

interface Props {
  source?: any;
  style?: ViewStyle;
  imageStyle?: ImageStyle;
  status?: "online" | "busy" | "edit";
  loading?: boolean
}

export const Avatar: React.FC<Props> = props => {

  if (props.loading) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator size="small" color={"grey"}/>
      </View>
    );
  }
  return (
    <View style={[props.style]}>
      <Image source={props.source.uri ? props.source : require("../../../../assets/user.png")} style={[styles.image, props.imageStyle]}/>
      {props.status &&
      <View style={[styles.status]}>
        <Ionicons
          name={props.status === "online" ? "ios-checkmark" : props.status === "busy" ? "ios-close" : "ios-camera"}
          color={YELLOW}
          size={22}
        />
      </View>}
    </View>
  );
};

const styles = StyleSheet.create({
  image: {
    width: 64,
    height: 64,
    borderRadius: 12,
  },
  loading: {
    height: 76,
    width: 76,
    justifyContent: "center",
    alignItems: "center",
  },
  status: {
    position: "absolute",
    width: 22,
    height: 22,
    bottom: 3,
    end: 3,
    borderRadius: 12,
    justifyContent: "center",
    alignItems: "center",
    borderColor: "transparent",
    borderWidth: 0,
  },
});
