import dayjs from "dayjs";

export const convertToISO8601 = (date: any) => {
  if (typeof (date) === "string" && date.includes("/")) {
    let splitDate = date.split("/");
    let day = splitDate[0];
    let month = splitDate[1];
    let year = splitDate[2];
    return year + "-" + month + "-" + day;
  } else {
    return dayjs(date).format("YYYY-MM-DD");
  }
};
