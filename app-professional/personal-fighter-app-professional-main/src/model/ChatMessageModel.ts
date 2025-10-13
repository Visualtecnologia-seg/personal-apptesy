export interface ChatMessageModel {
  _id: string,
  text: string,
  createdAt: string,
  user: {
    _id: number,
    name: string,
    avatar: string,
  },
}
