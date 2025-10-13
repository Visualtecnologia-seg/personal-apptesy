import React from "react";
import {StyleSheet, Text, View} from "react-native";
import {Theme} from "../../theme";
import {ProfessionalRating} from "./ProfessionalRating";
import {ReviewModel} from "../../model";

type TProps = {
  review: ReviewModel;
};

export const ProfessionalReview: React.FC<TProps> = props => {
  const review = props.review;
  return (
    <View style={styles.mainContainer}>
      <View style={styles.leftContainer}>
        <View style={styles.dateView}>
          <Text style={styles.date}>{review.date}</Text>
        </View>
        <View style={styles.ratingView}>
          <ProfessionalRating rating={Math.floor(review.rating)} size={14}/>
        </View>
      </View>
      <Text style={styles.comment}>{review.comment}</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  mainContainer: {
    width: "100%",
    backgroundColor: Theme.card.backgroundColor,
    borderRadius: 8,
    paddingHorizontal: 16,
    paddingVertical: 16
  },
  leftContainer: {
    flexDirection: "row",
    marginBottom: 10
  },
  dateView: {
    flex: 1,
    justifyContent: "center"
  },
  ratingView: {
    justifyContent: "center"
  },
  comment: {
    fontSize: Theme.textSize.primary,
    color: Theme.textColor.secondary,
  },
  date: {
    flex: 1,
    textAlign: "left",
    color: Theme.textColor.primary,
    fontWeight: "bold",
    fontSize: 16
  }
});
