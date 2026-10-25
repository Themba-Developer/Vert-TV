export const ACTIVATION_AMOUNT_CENTS=39900;
export function formatAmount(cents){if(!Number.isSafeInteger(cents)||cents<0)throw new Error('Invalid stored order amount');return `${Math.floor(cents/100)}.${String(cents%100).padStart(2,'0')}`}
export function moneyToCents(value){if(typeof value!=='string'||!/^\d{1,9}\.\d{2}$/.test(value))return null;const [whole,fraction]=value.split('.');const result=Number(whole)*100+Number(fraction);return Number.isSafeInteger(result)?result:null}
