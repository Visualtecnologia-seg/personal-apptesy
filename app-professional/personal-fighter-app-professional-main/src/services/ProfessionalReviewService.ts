import {get, post} from "./Api";
import {ReviewModel} from "../model";
import {ReqParamsModel} from "../model/ReqParamsModel";

export const getReviews = async (id: string, params: ReqParamsModel, filters?: ReqParamsModel): Promise<any> => {
  const reqParams = params ? params : filters;
  return await get("/reviews/users/" + id, reqParams);
};

export const postReview = async (data: ReviewModel): Promise<any> => {
  return await post("/reviews", data);
};
