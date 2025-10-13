import {ActivityIndicator, StyleSheet, View} from "react-native";
import {YELLOW} from "../../../theme";
import React from "react";

type TProps = {};

export const Loading: React.FC<TProps> = props => {
  return (
    <View style={styles.container}>
      <ActivityIndicator size="large" color={YELLOW}/>
    </View>
  )
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: "center"
  }
});
