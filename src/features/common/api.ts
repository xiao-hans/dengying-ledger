/**
 * 页面侧 API 门面：所有页面只通过这里访问后端（本地 mock 或真实 HTTP），
 * 不直接 import IndexedDB DAO，保证「切真实服务零改动」。
 */
import { post, postBody, get, call } from '../../api/client';
import { bootstrap, exportAll } from '../../db/bootstrap';
import { all } from '../../db/client';

export type Dict = Record<string, unknown>;

/** 页面启动前保证 mock 后端与 IndexedDB 已就绪（避免严格时序下的首次空白）。 */
export const ready: Promise<unknown> = bootstrap();

/** 本地字典读取：样本未暴露独立 List 端点的数据（标签等）从本地表读，功能面等价。 */
export const local = {
  tags: async (): Promise<Dict[]> => { await ready; return all<Dict>('tag'); },
  assets: async (): Promise<Dict[]> => { await ready; return all<Dict>('assets'); },
  bookList: async (): Promise<Dict[]> => { await ready; return all<Dict>('bill_book'); },
};

export const api = {
  /* Bills */
  billsPage: (pageNum = 1, updateDate = 0) => post<Dict>('Bills/PageList', { pageNum, updateDate }),
  billsCreate: (items: Dict[]) => postBody<Dict>('Bills/Create', { items }),
  billsUpdate: (items: Dict[]) => postBody<Dict>('Bills/Update', { items }),
  billsDelete: (items: Dict[]) => postBody<Dict>('Bills/Delete', { items }),
  billsCountOfHasPic: () => post<number>('Bills/BillsCountOfHasPic'),
  autoBillsCallback: (type = 0) => post<Dict>('Bills/AutoBillsCallBack', { type }),
  fixedBills: () => post<Dict>('Bills/GetFixedBills'),
  saveFixedBill: (fields: Dict, update = false) => post<Dict>(update ? 'Bills/UpdateFixedBill' : 'Bills/CreateFixedBill', fields),
  deleteFixedBill: (id: string | number) => post('Bills/DeleteFixedBill', { id }),
  importBills: (body: Dict) => postBody<Dict>('Bills/ImportBills', body),

  /* Categories / Tags / Budget / Rules */
  mainCates: () => post<{ expense: Dict[]; income: Dict[]; transfer: Dict[] }>('Cate/GetMainCates'),
  cateIcons: () => post<Dict[]>('Cate/GetCateIcons'),
  saveCate: (fields: Dict, update = false) => post<Dict>(update ? 'Cate/Update' : 'Cate/Create', fields),
  deleteCate: (id: number) => post('Cate/Delete', { id }),
  updateCateExtend: (items: Dict[]) => postBody('Cate/UpdateExtend', { items }),
  tagCreate: (bookId: number, name: string) => post<Dict>('MemberTag/Create', { BillBookId: bookId, name }),
  tagUpdate: (tagId: number, name: string) => post<Dict>('MemberTag/Update', { tagId, name }),
  tagDelete: (tagId: number) => post('MemberTag/Delete', { tagId }),
  tagSort: (items: Dict[]) => postBody('MemberTag/UpdateSort', items.length ? { items } : {}),
  budgetGet: (bookId = 1, day = 0) => post<Dict>('Budget/GetBudget', { billsBookId: bookId, day: day || undefined }),
  budgetList: () => get<Dict[]>('Budget/GetBudgetList'),
  budgetYearly: (year: number) => post<Dict[]>('Budget/GetYearlyBudgets', { year }),
  saveBudget: (fields: Dict, update = false) => post<Dict>(update ? 'Budget/UpdateBudget' : 'Budget/CreateBudget', fields),
  deleteBudget: (fields: Dict) => post('Budget/DeleteBudget', fields),
  saveCateBudget: (fields: Dict, update = false) => post<Dict>(update ? 'Budget/UpdateBudgetCate' : 'Budget/CreateBudgetCate', fields),
  deleteCateBudget: (fields: Dict) => post('Budget/DeleteBudgetCate', fields),
  ruleList: () => post<Dict[]>('KeyWordMatchingRule/List'),
  saveRule: (fields: Dict, update = false) => post<Dict>(update ? 'KeyWordMatchingRule/Update' : 'KeyWordMatchingRule/Create', fields),
  deleteRule: (id: number) => post('KeyWordMatchingRule/Delete', { id }),

  /* Books */
  bookList: () => post<Dict[]>('BillsBook/GetList'),
  bookIcons: () => post<Dict[]>('BillsBook/GetBillsBookIcons'),
  bookInfoByCode: (code: string) => post<Dict>('BillsBook/GetInfoByCode', { code }),
  bookCreate: (fields: Dict) => post<Dict>('BillsBook/Create', fields),
  bookUpdate: (fields: Dict) => post<Dict>('BillsBook/Update', fields),
  bookDelete: (id: number) => post('BillsBook/Delete', { billsBookId: id }),
  bookInviteCode: (id: number) => post<string>('BillsBook/CreateInviteCode', { billsBookId: id }),
  bookJoin: (id: number) => post<Dict>('BillsBook/Join', { billsBookId: id }),
  bookExit: (id: number) => post('BillsBook/Exit', { billsBookId: id }),
  bookRemoveMember: (id: number, memberId: number) => post('BillsBook/RemoveMember', { billsBookId: id, memberId }),

  /* Assets / Installment / SavePlan */
  assetTypes: () => post<Dict[]>('FundAccount/GetFundAccountTypes'),
  iou: (status = 0) => post<string>('FundAccount/GetIOU', { status }),
  saveAsset: (fields: Dict, update = false) => post<Dict>(update ? 'FundAccount/Update' : 'FundAccount/Create', fields),
  deleteAsset: (id: number) => post('FundAccount/Delete', { id }),
  assetStatus: (id: number, status: number) => post('FundAccount/UpdateStatus', { id, status }),
  installmentAll: () => post<Dict[]>('ConsumptionInstallment/AllPlan'),
  installmentCreate: (body: Dict) => postBody<Dict>('ConsumptionInstallment/Create', body),
  installmentGet: (planId: string) => post<Dict>('ConsumptionInstallment/GetByPlanId', { planId }),
  installmentDelete: (planId: string, isSaveBill = 1) => post('ConsumptionInstallment/Delete', { planId, isSaveBill }),
  installmentPayOff: (fields: Dict) => post<Dict>('ConsumptionInstallment/PayOff', fields),
  savePlans: () => get<Dict[]>('SavePlan/GetSavePlans'),
  fileSavePlans: () => get<Dict[]>('SavePlan/GetFileSavePlans'),
  planTypes: () => get<Dict[]>('SavePlan/GetSavePlanType'),
  planIcons: () => get<Dict[]>('SavePlan/GetIcon'),
  wishes: () => get<string[]>('SavePlan/Wishes'),
  plansByType: (type: number) => post<Dict[]>('SavePlan/GetSavePlansByType', { type }),
  plansByQuery: (body: Dict) => postBody<Dict[]>('SavePlan/GetSavePlansByQuery', body),
  planItems: (planId: string) => postBody<Dict>('SavePlan/SavePlanItemsById', { planId }),
  createSavePlan: (body: Dict) => postBody<Dict>('SavePlan/CreateSavePlan', body),
  updateSavePlan: (body: Dict) => postBody('SavePlan/UpdateSavePlan', body),
  updateSavePlanStatus: (body: Dict) => postBody<string[]>('SavePlan/UpdateSavePlanStatus', body),
  createPlanItem: (body: Dict) => postBody<Dict>('SavePlan/CreateSavePlanItem', body),
  updatePlanItem: (body: Dict) => postBody('SavePlan/UpdateSavePlanItem', body),
  updatePlanItemStatus: (body: Dict) => postBody('SavePlan/UpdateSavePlanItemStatus', body),

  /* Import / Upload / Refund */
  importFileList: () => post<Dict[]>('MemberImportBillFile/List'),
  importFileDelete: (id: string) => post('MemberImportBillFile/Delete', { id }),
  uploadBills: (body: Dict) => postBody<Dict>('Upload/UploadBills', body),
  uploadImages: (body: Dict) => postBody<Dict>('Upload/UploadImages', body),
  uploadImg: (imgType = 0) => post<Dict>('Upload/UploadImg', { imgType }),
  refundCreate: (body: Dict) => postBody<Dict>('BillRefund/Create', body),
  refundUpdate: (body: Dict) => postBody<Dict>('BillRefund/Update', body),
  refundDelete: (id: string) => postBody('BillRefund/Delete', { id }),

  /* Account / Member / Settings / Config */
  sendCode: (phone: string) => post<Dict>('Account/SendCode', { phone }),
  loginByPhone: (phone: string, code: string) => post<Dict>('Account/LoginByPhone', { phone, code }),
  loginByDevice: () => post<Dict>('Account/LoginByDevice'),
  loginThird: (type: 'wechat' | 'qq', fields: Dict) => post<Dict>(type === 'wechat' ? 'Account/LoginByWechat' : 'Account/LoginByQQ', fields),
  bindPhone: (fields: Dict) => post<Dict>('Account/BindPhone', fields),
  unbind: () => post('Account/Unbind'),
  activate: (distinctId: string) => post('Account/ActivateV3', { distinctId }),
  memberInfo: () => post<Dict>('Member/GetInfo'),
  memberSetting: () => post<Dict>('Member/GetSetting'),
  updateSetting: (key: string, value: string) => post('Member/UpdateSetting', { key, value }),
  updateNickname: (nickname: string) => post('Member/UpdateNickname', { nickname }),
  refreshToken: (gtCid = '') => post<Dict>('Member/RefreshToken', { gtCid }),
  contractInfo: () => get<Dict>('Member/GetMemberContractInfo'),
  migrateOldUser: () => post<Dict>('Member/InitSSOldUserInfo'),
  reminders: () => post<Dict[]>('Member/MemberReminderList'),
  saveReminder: (fields: Dict, update = false) => post(update ? 'Member/UpdateReminder' : 'Member/CreateReminder', fields),
  serverConfig: (fields: Dict) => post<Dict>('Config/GetV2', fields, { urlType: 'configManage' }),
  behavior: (body: Dict) => postBody('Behavior/Add', body),

  /* Order / VIP / Operation / PersonCenter */
  vipConfigs: () => post<Dict>('Order/v2/VipConfigs'),
  createOrder: (fields: Dict) => post<Dict>('Order/v2/CreateOrder', fields),
  checkOrder: (orderId: string) => post<Dict>('Order/CheckStatus', { orderId }),
  discountConfig: () => post<Dict>('Order/GetRechargeDiscountConfig'),
  alipayUnsign: () => postBody('Order/AlipayUnSign', {}),
  groupVipList: () => get<Dict[]>('VipGroupBuy/List'),
  groupVipBind: (showId: string) => postBody<Dict[]>('VipGroupBuy/Bind', { showId }),
  groupVipCheck: (showId: string) => postBody<Dict>('VipGroupBuy/GetByShowId', { showId }),
  dialog: () => post<Dict>('Operation/GetDialog'),
  updateRemind: () => post<Dict>('Operation/GetUpdateRemind'),
  personIcons: () => get<Dict[]>('PersonCenter/GetPersonCenterIcons'),

  /* 动态 @Url */
  download: (url: string) => call<Dict>('GET', '@Url:GET:download', { url }),
  uploadOss: (url: string, body: Dict) => postBody<Dict>('@Url:POST:uploadOss', body, { url }),

  /* 本地诊断（非样本接口） */
  exportAll: async () => { await ready; return exportAll(); },
};
