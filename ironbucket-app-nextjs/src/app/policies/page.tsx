import Link from 'next/link';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';

export default function PoliciesIndexPage() {
  return (
    <section className="space-y-6" data-testid="policy-index-page">
      <Card>
        <CardHeader>
          <CardTitle>Policy Engine UI</CardTitle>
          <CardDescription>Choose a tenant scope to enter policy management.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-3">
          <Button asChild><Link href="/policies/alice">Open tenant alice</Link></Button>
          <Button asChild variant="secondary"><Link href="/policies/bob">Open tenant bob</Link></Button>
        </CardContent>
      </Card>
    </section>
  );
}
