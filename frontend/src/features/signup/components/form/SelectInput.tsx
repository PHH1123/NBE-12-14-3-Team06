import DropdownSelect from '@/components/ui/DropdownSelect';

type Props = {
  id: string;
  name: string;
  value: string;
  options: { value: string; label: string }[];
  placeholder: string;
  onValueChange: (value: string) => void;
  required?: boolean;
  disabled?: boolean;
};

export default function SelectInput(props: Props) {
  return <DropdownSelect {...props} size="form" />;
}
