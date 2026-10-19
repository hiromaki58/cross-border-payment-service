variable "backend_desired_count" {
  description = "backend のタスク数。初回は 0 で作り、DB とアプリ用ユーザーを作成してから 1 にする"
  type        = number
  default     = 1
}
