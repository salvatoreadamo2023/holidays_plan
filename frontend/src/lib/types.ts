export type Role = 'USER' | 'TEAM_LEAD' | 'PROJECT_MANAGER' | 'ADMIN'
export type AbsenceType = 'FERIE' | 'PERMESSO' | 'MALATTIA'
export type Status = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'CANCEL_REQUESTED' | 'CANCELLED'

export interface UserSummary {
  id: number; fullName: string; username: string; role: Role
  areaId: number | null; areaName: string | null; teamId: number | null; teamName: string | null
}

export interface RequestView {
  id: number; user: UserSummary; type: AbsenceType; status: Status
  startDate: string; endDate: string; halfDay: boolean; hours: number | null; amount: number
  note: string | null; decisionNote: string | null; decidedBy: string | null; decidedAt: string | null; createdAt: string
}

export interface Balance {
  year: number
  leaveTotal: number; leaveUsed: number; leavePending: number; leaveAvailable: number
  permitTotal: number; permitUsed: number; permitPending: number; permitAvailable: number
  sickDays: number
}

export interface Dashboard {
  me: UserSummary; balance: Balance; upcoming: RequestView[]; toApprove: number; absentToday: string[]
}

export interface CalendarView {
  from: string; to: string
  days: { date: string; weekend: boolean; holiday: string | null }[]
  people: { id: number; fullName: string; role: Role; teamId: number | null; teamName: string | null }[]
  entries: { requestId: number | null; userId: number; startDate: string; endDate: string; type: AbsenceType | 'ASSENZA'; status: Status; halfDay: boolean; hours: number | null }[]
  areas: { id: number; name: string }[]
  teams: { id: number; name: string }[]
  areaId: number | null; teamId: number | null
}

export interface Preview { amount: number; unit: string; availableAfter: number | null; colleaguesAbsent: string[]; warnings: string[] }

export interface CreateRequest { type: AbsenceType; startDate: string; endDate: string; halfDay: boolean; hours: number | null; note: string }

export interface UserAdmin {
  id: number; username: string; email: string; fullName: string; role: Role
  areaId: number | null; areaName: string | null; teamId: number | null; teamName: string | null
  active: boolean; annualLeaveDays: number; annualPermitHours: number
}
export interface AreaAdmin { id: number; name: string; managerId: number | null; managerName: string | null }
export interface TeamAdmin { id: number; name: string; areaId: number; areaName: string; leaderId: number | null; leaderName: string | null }
export interface HolidayAdmin { id: number | null; date: string; name: string; national: boolean }
