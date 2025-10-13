import {api} from "./Api";
import HandleErrors from "./Exceptions/HandleExceptions";
import Resizer from "react-image-file-resizer";

export const s3UploadService = async (data?: any) => {
  const token = JSON.parse(localStorage.getItem("@blueshark-personal-token") as string);
  let response: any = {};

  if (data.get("file").size >= 1048576) {
    const file = await resizeFile(data.get("file"))
    data.set("file", file, "file.jpg");
  }

  await api
    .post("/s3/upload-image", data, {
      headers: {
        "Content-Type": "multipart/form-data",
        Authorization: "Bearer " + token,
      },
    })
    .then(res => {
      response.data = res.data;
    })
    .catch(error => {
      response.error = {type: "alert", notification: HandleErrors(error)};
    });
  return response;
};

export const imageUpload = async (f: any) => {
  let file = f[0];
  let typeImage = file.name.substring(file.name.lastIndexOf(".") + 1);
  if (typeImage === "jpeg") {
    typeImage = "jpg";
  }
  file = await resizeFile(file);

  if (file.size >= 1048576) {
    return "Error";
  }

  const data = new FormData();
  data.append("file", file, "file.jpg");
  return await s3UploadService(data);
};

export const resizeFile = async (file) => {
  const quality = file.size < 500000 ? 85 : 75;
  return new Promise((resolve) => {
    Resizer.imageFileResizer(
      file,
      1440,
      2560,
      "JPEG",
      quality,
      0,
      (uri) => {
        resolve(uri);
      },
      "file",
    );
  });
};
