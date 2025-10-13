import React from "react";
import {ScrollView, StyleSheet} from "react-native";

import RenderHTML from "react-native-render-html";


import {privacyPolicy} from "./component/PrivacyPolicyAndTermsOfService";
import {themeStyles} from "../../config/theme/Styles";

interface Props {
}

export const PrivacyPolicy: React.FC<Props> = props => {

  return (
    <ScrollView
      style={themeStyles.text}
    >
      <RenderHTML
        baseFontStyle={{color: "white"}}
        source={{html: privacyPolicy}}
      />
    </ScrollView>
  );
};
const styles = StyleSheet.create({});
